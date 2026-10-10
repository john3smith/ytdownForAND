package com.local.ytdown;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback CONNECT relay. No TLS termination, root certificate, VPN or remote proxy. */
final class LocalHttpsRelay implements Closeable {
    interface Connector { Socket connect(String host) throws IOException; }
    private final Connector connector;
    private final ServerSocket listener;
    private final Semaphore slots = new Semaphore(16);
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "https-assist-io"); thread.setDaemon(true); return thread;
            });
    private final AtomicInteger fragmented = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private volatile boolean closed;

    LocalHttpsRelay() throws IOException {
        this(null);
    }

    // Package-private test seam; the app uses only the restricted default connector.
    LocalHttpsRelay(Connector testConnector) throws IOException {
        connector = testConnector == null ? this::connect : testConnector;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16);
        Thread accept = new Thread(this::acceptLoop, "https-assist-accept");
        accept.setDaemon(true);
        accept.start();
    }

    String proxyUrl() { return "http://127.0.0.1:" + listener.getLocalPort(); }
    int fragmentedCount() { return fragmented.get(); }
    int failureCount() { return failed.get(); }

    private void acceptLoop() {
        while (!closed) {
            Socket client = null;
            try {
                client = listener.accept();
                if (!client.getInetAddress().isLoopbackAddress() || !slots.tryAcquire()) {
                    closeSocket(client); continue;
                }
                if (!track(client)) { slots.release(); continue; }
                Socket accepted = client;
                try { workers.execute(() -> handle(accepted)); }
                catch (RuntimeException rejected) { slots.release(); closeSocket(client); }
            } catch (IOException stopped) {
                if (!closed) failed.incrementAndGet();
                closeSocket(client);
            }
        }
    }

    private void handle(Socket client) {
        Socket upstream = null;
        boolean connected = false;
        try {
            client.setSoTimeout(15000);
            client.setTcpNoDelay(true);
            InputStream input = client.getInputStream();
            String host = HttpsRelayPolicy.connectHost(readHeader(input));
            if (host == null) {
                reply(client, "403 Forbidden"); return;
            }
            upstream = connector.connect(host);
            if (!track(upstream)) return;
            reply(client, "200 Connection Established");
            connected = true;
            byte[][] records = TlsClientHello.split(TlsClientHello.readRecord(input));
            OutputStream outgoing = upstream.getOutputStream();
            outgoing.write(records[0]);
            outgoing.flush();
            Thread.sleep(20);
            outgoing.write(records[1]);
            outgoing.flush();
            fragmented.incrementAndGet();
            client.setSoTimeout(180000);
            upstream.setSoTimeout(180000);
            Socket server = upstream;
            workers.execute(() -> {
                try { pump(server.getInputStream(), client.getOutputStream()); }
                catch (IOException ignored) { /* no payload or host logging */ }
                finally { closeSocket(server); closeSocket(client); }
            });
            pump(input, outgoing);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException error) {
            if (!closed && !client.isClosed()) failed.incrementAndGet();
            if (!connected) try { reply(client, "502 Bad Gateway"); } catch (IOException ignored) { }
        } finally {
            closeSocket(upstream);
            closeSocket(client);
            slots.release();
        }
    }

    private Socket connect(String host) throws IOException {
        IOException failure = new IOException("No permitted upstream address");
        int attempted = 0;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (!HttpsRelayPolicy.publicAddress(address) || ++attempted > 4) continue;
            Socket socket = new Socket();
            if (!track(socket)) throw new IOException("Relay stopped");
            try {
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(address, 443), 5000);
                return socket;
            } catch (IOException error) { failure = error; closeSocket(socket); }
        }
        throw failure;
    }

    private static String readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int tail = 0;
        while (header.size() < 4096) {
            int value = input.read();
            if (value < 0 || value > 127) throw new IOException("Invalid CONNECT header");
            header.write(value);
            tail = (tail << 8) | value;
            if (tail == 0x0d0a0d0a) return header.toString(StandardCharsets.US_ASCII.name());
        }
        throw new IOException("CONNECT header too large");
    }

    private static void reply(Socket socket, String status) throws IOException {
        socket.getOutputStream().write(("HTTP/1.1 " + status + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private static void pump(InputStream source, OutputStream target) throws IOException {
        byte[] buffer = new byte[32768];
        int read;
        while ((read = source.read(buffer)) != -1) target.write(buffer, 0, read);
    }

    private synchronized boolean track(Socket socket) {
        if (closed) { closeSocket(socket); return false; }
        sockets.add(socket);
        return true;
    }

    private void closeSocket(Socket socket) {
        if (socket == null) return;
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket socket : sockets) closeSocket(socket);
        workers.shutdownNow();
    }
}
