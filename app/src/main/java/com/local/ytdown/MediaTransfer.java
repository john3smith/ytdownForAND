package com.local.ytdown;

import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Shared image/video stream copy. Emit progress only when its integer value changes. */
final class MediaTransfer {
    private MediaTransfer() {}
    static long copy(InputStream input, OutputStream output, long length, int index, int total,
                     BooleanSupplier cancelled, IntConsumer progress)
            throws IOException, InterruptedException {
        if (total <= 0 || index < 0 || index >= total) throw new IllegalArgumentException("Invalid batch");
        byte[] buffer = new byte[64 * 1024];
        long received = 0;
        int previous = -1;
        while (true) {
            if (cancelled.getAsBoolean()) throw new InterruptedException("다운로드가 취소되었습니다.");
            int count = input.read(buffer);
            if (cancelled.getAsBoolean()) throw new InterruptedException("다운로드가 취소되었습니다.");
            if (count < 0) break;
            if (count == 0) continue;
            if (length >= 0 && count > length - received) {
                throw new IOException("다운로드 크기가 응답의 파일 크기보다 큽니다.");
            }
            output.write(buffer, 0, count);
            received += count;
            double itemProgress = length > 0 ? Math.min(1.0, (double) received / length) : 0;
            int value = (int) Math.round(((index + itemProgress) / total) * 100.0);
            if (value != previous) {
                progress.accept(value);
                previous = value;
            }
        }
        // EOF alone is not proof of a complete image/video. Throw before callers
        // mark MediaStore entries ready so their existing failure cleanup runs.
        if (length >= 0 && received != length) {
            throw new EOFException("다운로드가 파일 크기에 도달하기 전에 종료되었습니다.");
        }
        if (received == 0) throw new IOException("다운로드 파일이 비어 있습니다.");
        return received;
    }
}
