package com.local.ytdown;

import java.net.InetAddress;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpsRelayPolicyTest {
    @Test public void allowedDomainsRespectBoundaries() {
        assertTrue(HttpsRelayPolicy.allowedHost("www.pornhub.com"));
        assertTrue(HttpsRelayPolicy.allowedHost("a.b.phncdn.com"));
        assertTrue(HttpsRelayPolicy.allowedHost("phprcdn.com"));
        for (String host : new String[]{null,"notpornhub.com","pornhub.com.evil.test",
                "pornhub.com@evil.test","127.0.0.1",".pornhub.com","a..pornhub.com","-a.pornhub.com"}) {
            assertFalse(HttpsRelayPolicy.allowedHost(host));
        }
    }
    @Test public void onlyHttpsConnectRequestsAreAllowed() {
        assertEquals("www.pornhub.com", HttpsRelayPolicy.connectHost("CONNECT www.pornhub.com:443 HTTP/1.1\r\n\r\n"));
        for (String value : new String[]{null,"GET https://www.pornhub.com/ HTTP/1.1\r\n\r\n",
                "CONNECT www.pornhub.com:80 HTTP/1.1\r\n\r\n","CONNECT localhost:443 HTTP/1.1\r\n\r\n",
                "CONNECT www.pornhub.com:443 HTTP/1.1\n\n","CONNECT  www.pornhub.com:443 HTTP/1.1\r\n\r\n"}) {
            assertNull(HttpsRelayPolicy.connectHost(value));
        }
    }
    @Test public void privateAndReservedAddressesCannotBeUpstream() throws Exception {
        for (String ip : new String[]{"0.0.0.0","127.0.0.1","10.0.0.1","172.16.0.1","192.168.1.1",
                "169.254.1.1","100.64.0.1","198.18.0.1","192.0.2.1","198.51.100.1","203.0.113.1",
                "224.0.0.1","255.255.255.255","::1","fe80::1","fc00::1","fd00::1"}) {
            assertFalse(ip, HttpsRelayPolicy.publicAddress(InetAddress.getByName(ip)));
        }
        assertTrue(HttpsRelayPolicy.publicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(HttpsRelayPolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }
}
