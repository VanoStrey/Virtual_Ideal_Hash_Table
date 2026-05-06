package org.examle.vht;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

public class OptimalVHTBuilder {
    private static final Logger log = VHTLogger.LOGGER;
    private static final int THREADS = 4;
    private static final long FLUSH_INTERVAL_MS = 2000;
    private static final long SAVE_COUNTS_EVERY = 200_000;
    private static final String CHECKPOINT_FILE = "build_checkpoint.dat";
    private static final String SLOT_COUNTS_FILE = "slot_counts.bin";

    // Переиспользуемые буферы для pack
    private static final ThreadLocal<ByteBuffer> packedBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(4));

    public static void main(String[] args) throws Exception {
        // warm‑up
        log.info("Warming up JIT & SHA‑256 …");
        long warmEnd = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < warmEnd) {
            SHA256Hasher.hash("ABCDE");
        }
        log.info("Warm‑up completed.");

        log.info("=== Optimal VHT Builder (lock‑free, persistent slot counts) ===");
        log.info(String.format("Preimages: %,d | Slots: %,d | Cells/slot: %d",
                Config.TOTAL_PREIMAGES, Config.TOTAL_SLOTS, Config.CELLS_PER_SLOT));
        log.info(String.format("Threads: %d", THREADS));
        log.info(String.format("Primary file: %s (%.1f GiB)", Config.PRIMARY_FILE,
                Config.FILE_SIZE / 1_073_741_824.0));

        File pFile = new File(Config.PRIMARY_FILE);
        if (!pFile.exists() || pFile.length() != Config.FILE_SIZE) {
            log.info("Creating primary file...");
            try (RandomAccessFile raf = new RandomAccessFile(Config.PRIMARY_FILE, "rw")) {
                raf.setLength(Config.FILE_SIZE);
            }
            log.info("Primary file created.");
        }

        AtomicIntegerArray slotCounts = new AtomicIntegerArray((int) Config.TOTAL_SLOTS);

        if (new File(SLOT_COUNTS_FILE).exists()) {
            log.info("Loading saved slot counts...");
            try (RandomAccessFile raf = new RandomAccessFile(SLOT_COUNTS_FILE, "r")) {
                MappedByteBuffer map = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, Config.TOTAL_SLOTS);
                for (int i = 0; i < Config.TOTAL_SLOTS; i++) {
                    slotCounts.set(i, map.get(i) & 0xFF);
                }
            }
        }

        AtomicLong globalIdx = new AtomicLong(loadCheckpoint());
        AtomicLong processed = new AtomicLong(globalIdx.get());
        AtomicLong primaryWritten = new AtomicLong(0);
        AtomicInteger activeThreads = new AtomicInteger(THREADS);
        long startTime = System.currentTimeMillis();

        // Поток сохранения счётчиков
        Thread saver = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long lastSaved = processed.get();
                    while (processed.get() - lastSaved < SAVE_COUNTS_EVERY && activeThreads.get() > 0) {
                        Thread.sleep(1000);
                    }
                    if (activeThreads.get() == 0) break;
                    try (RandomAccessFile raf = new RandomAccessFile(SLOT_COUNTS_FILE, "rw")) {
                        MappedByteBuffer map = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, Config.TOTAL_SLOTS);
                        for (int i = 0; i < Config.TOTAL_SLOTS; i++) {
                            map.put(i, (byte) slotCounts.get(i));
                        }
                        map.force();
                    }
                    log.info("Slot counts saved to disk.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.log(Level.SEVERE, "Error saving slot counts", e);
            }
        }, "Saver");
        saver.setDaemon(true);
        saver.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.warning("Saving checkpoint & slot counts...");
            saveCheckpoint(globalIdx.get());
            try (RandomAccessFile raf = new RandomAccessFile(SLOT_COUNTS_FILE, "rw")) {
                MappedByteBuffer map = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, Config.TOTAL_SLOTS);
                for (int i = 0; i < Config.TOTAL_SLOTS; i++) {
                    map.put(i, (byte) slotCounts.get(i));
                }
                map.force();
            } catch (IOException e) {
                log.severe("Final save of slot counts failed: " + e.getMessage());
            }
        }));

        Thread monitor = new Thread(() -> {
            long lastProc = processed.get();
            long lastTime = System.currentTimeMillis();
            while (activeThreads.get() > 0) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                long curProc = processed.get();
                long now = System.currentTimeMillis();
                double elapsed = (now - startTime) / 1000.0;
                double speed = (curProc - lastProc) * 1000.0 / Math.max(1, now - lastTime);
                double avgSpeed = curProc / Math.max(1, elapsed);
                double progress = curProc * 100.0 / Config.TOTAL_PREIMAGES;
                double eta = (Config.TOTAL_PREIMAGES - curProc) / Math.max(1, speed);
                log.info(String.format("[PROGRESS] %.2f%% (%,d) | speed: %,.0f/s (avg %,.0f/s) | primary: %,d | ETA: %.0f min",
                        progress, curProc, speed, avgSpeed, primaryWritten.get(), eta / 60));
                lastProc = curProc;
                lastTime = now;
            }
        }, "Monitor");
        monitor.setDaemon(true);
        monitor.start();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                try (FileChannel channel = FileChannel.open(Paths.get(Config.PRIMARY_FILE),
                        StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                    long localProcessed = 0, localPrim = 0;
                    long lastFlush = System.currentTimeMillis();
                    ByteBuffer localBuf = packedBuffer.get(); // переиспользуемый буфер
                    while (true) {
                        long idx = globalIdx.getAndIncrement();
                        if (idx >= Config.TOTAL_PREIMAGES) break;

                        String preimage = PreimageCodec.indexToPreimage((int) idx);
                        byte[] packed = PreimageCodec.pack(preimage);
                        byte[] hash = SHA256Hasher.hash(preimage);
                        int slot = SHA256Hasher.slotIndex(hash);

                        int cell = slotCounts.getAndIncrement(slot);
                        if (cell < Config.CELLS_PER_SLOT) {
                            long pos = (long) slot * Config.SLOT_BYTES + (long) cell * Config.CELL_BYTES;
                            localBuf.clear();
                            localBuf.put(packed);
                            localBuf.flip();
                            channel.write(localBuf, pos);
                            localPrim++;
                        }
                        localProcessed++;

                        long now = System.currentTimeMillis();
                        if (now - lastFlush >= FLUSH_INTERVAL_MS) {
                            processed.addAndGet(localProcessed);
                            primaryWritten.addAndGet(localPrim);
                            localProcessed = 0;
                            localPrim = 0;
                            lastFlush = now;
                        }
                    }
                    if (localProcessed > 0) {
                        processed.addAndGet(localProcessed);
                        primaryWritten.addAndGet(localPrim);
                    }
                } catch (IOException e) {
                    log.log(Level.SEVERE, "I/O error in worker", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        saver.interrupt();
        monitor.interrupt();
        monitor.join();

        saveCheckpoint(globalIdx.get());
        try (RandomAccessFile raf = new RandomAccessFile(SLOT_COUNTS_FILE, "rw")) {
            MappedByteBuffer map = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, Config.TOTAL_SLOTS);
            for (int i = 0; i < Config.TOTAL_SLOTS; i++) {
                map.put(i, (byte) slotCounts.get(i));
            }
            map.force();
        }
        new File(CHECKPOINT_FILE).delete();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info(String.format("Total build time: %.1f min", totalTime / 60000.0));
        log.info(String.format("Primary written: %,d", primaryWritten.get()));
    }

    private static void saveCheckpoint(long idx) {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(CHECKPOINT_FILE))) {
            dos.writeLong(idx);
        } catch (IOException e) { log.warning("Checkpoint save failed: " + e.getMessage()); }
    }

    private static long loadCheckpoint() {
        File f = new File(CHECKPOINT_FILE);
        if (!f.exists()) return 0;
        try (DataInputStream dis = new DataInputStream(new FileInputStream(f))) {
            return dis.readLong();
        } catch (IOException e) { log.warning("Checkpoint load failed: " + e.getMessage()); return 0; }
    }
}