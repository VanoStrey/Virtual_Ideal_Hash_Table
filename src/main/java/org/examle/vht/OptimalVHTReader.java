package org.examle.vht;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Arrays;

public class OptimalVHTReader implements AutoCloseable {
    private final MappedByteBuffer[] segments;
    private final int segSize = 1 << 30; // 1 ГБ
    private final int segCount;

    public OptimalVHTReader(String filePath) throws IOException {
        long fileSize = Files.size(Paths.get(filePath));
        segCount = (int) ((fileSize + segSize - 1) / segSize);
        segments = new MappedByteBuffer[segCount];
        try (FileChannel ch = FileChannel.open(Paths.get(filePath), StandardOpenOption.READ)) {
            for (int i = 0; i < segCount; i++) {
                long offset = (long) i * segSize;
                long size = Math.min(segSize, fileSize - offset);
                segments[i] = ch.map(FileChannel.MapMode.READ_ONLY, offset, size);
            }
        }
    }

    public String search(byte[] targetHash) throws IOException {
        int slot = SHA256Hasher.slotIndex(targetHash);
        byte[] slotData = readSlot(slot);
        for (int cell = 0; cell < Config.CELLS_PER_SLOT; cell++) {
            int off = cell * Config.CELL_BYTES;
            boolean empty = true;
            for (int i = 0; i < Config.CELL_BYTES; i++) {
                if (slotData[off + i] != 0) { empty = false; break; }
            }
            if (empty) continue;
            byte[] packed = Arrays.copyOfRange(slotData, off, off + Config.CELL_BYTES);
            String preimage = PreimageCodec.unpack(packed);
            if (Arrays.equals(SHA256Hasher.hash(preimage), targetHash)) return preimage;
        }
        return null;
    }

    private byte[] readSlot(int slotIndex) {
        long pos = (long) slotIndex * Config.SLOT_BYTES;
        int seg = (int) (pos / segSize);
        int off = (int) (pos % segSize);
        byte[] slot = new byte[Config.SLOT_BYTES];
        segments[seg].position(off);
        segments[seg].get(slot);
        return slot;
    }

    @Override
    public void close() {}
}