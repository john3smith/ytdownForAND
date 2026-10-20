package com.local.ytdown;

import java.io.IOException;
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
            if (count < 0) break;
            if (cancelled.getAsBoolean()) throw new InterruptedException("다운로드가 취소되었습니다.");
            if (count == 0) continue;
            output.write(buffer, 0, count);
            received += count;
            double itemProgress = length > 0 ? Math.min(1.0, (double) received / length) : 0;
            int value = (int) Math.round(((index + itemProgress) / total) * 100.0);
            if (value != previous) {
                progress.accept(value);
                previous = value;
            }
        }
        return received;
    }
}
