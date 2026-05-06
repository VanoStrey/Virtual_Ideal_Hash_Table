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
    private static final String CHECKPOINT_FILE = "build_checkpoint.dat";

    public static void main(String[] args) throws Exception {
        log.info(String.format("=== Optimal VHT Builder (lock‑free) ==="));
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
        AtomicLong globalIdx = new AtomicLong(loadCheckpoint());
        AtomicLong processed = new AtomicLong(globalIdx.get());
        AtomicLong primaryWritten = new AtomicLong(0);
        AtomicInteger activeThreads = new AtomicInteger(THREADS);
        long startTime = System.currentTimeMillis();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.warning("Saving checkpoint...");
            saveCheckpoint(globalIdx.get());
        }));

        // Монитор
        Thread monitor = new Thread(() -> {
            long lastProc = processed.get();
            long lastTime = System.currentTimeMillis();
            while (activeThreads.get() > 0) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                long curProc = processed.get();
                long curPrim = primaryWritten.get();
                long now = System.currentTimeMillis();
                double elapsed = (now - startTime) / 1000.0;
                double speed = (curProc - lastProc) * 1000.0 / Math.max(1, now - lastTime);
                double avgSpeed = curProc / Math.max(1, elapsed);
                double progress = curProc * 100.0 / Config.TOTAL_PREIMAGES;
                double eta = (Config.TOTAL_PREIMAGES - curProc) / Math.max(1, speed);
                log.info(String.format("[PROGRESS] %.2f%% (%,d) | speed: %,.0f/s (avg %,.0f/s) | primary: %,d | ETA: %.0f min",
                        progress, curProc, speed, avgSpeed, curPrim, eta / 60));
                lastProc = curProc;
                lastTime = now;
            }
        }, "Monitor");
        monitor.setDaemon(true);
        monitor.start();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try (FileChannel channel = FileChannel.open(Paths.get(Config.PRIMARY_FILE),
                        StandardOpenOption.WRITE, StandardOpenOption.READ)) {
                    long localProcessed = 0, localPrim = 0;
                    long lastFlush = System.currentTimeMillis();
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
                            channel.write(ByteBuffer.wrap(packed), pos);
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
                    log.fine(String.format("[T%d] finished. Last idx: %,d", threadId, globalIdx.get() - 1));
                } catch (IOException e) {
                    log.log(Level.SEVERE, String.format("[T%d] I/O error", threadId), e);
                } finally {
                    activeThreads.decrementAndGet();
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        monitor.interrupt();
        monitor.join();

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