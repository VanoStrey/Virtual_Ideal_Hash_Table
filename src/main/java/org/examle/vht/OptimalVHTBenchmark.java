package org.examle.vht;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class OptimalVHTBenchmark {
    private static final int TEST_HASH_COUNT = 100;
    private static final int MAX_LENGTH_FOR_TEST = 4;
    private static final boolean PRINT_EACH_RESULT = true;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Optimal VHT Benchmark ===");
        System.out.printf("Alphabet: %s (base %d)%n", Config.ALPHABET, Config.BASE);
        System.out.printf("Max preimage length: %d%n", MAX_LENGTH_FOR_TEST);
        System.out.printf("Total preimages: %,d%n", Config.TOTAL_PREIMAGES);

        if (!Files.exists(Paths.get(Config.PRIMARY_FILE))) {
            System.err.println("Primary file not found! Build the table first.");
            return;
        }
        long fileSize = Files.size(Paths.get(Config.PRIMARY_FILE));
        System.out.printf("Primary file size: %.2f GiB%n", fileSize / 1_073_741_824.0);

        // Генерируем случайные прообразы (не просто хеши)
        List<String> preimages = generateRandomPreimages(TEST_HASH_COUNT);
        System.out.printf("Generated %,d random preimages (lengths 1-%d).%n", preimages.size(), MAX_LENGTH_FOR_TEST);

        System.out.println("Starting benchmark...");
        try (OptimalVHTReader reader = new OptimalVHTReader(Config.PRIMARY_FILE)) {
            long totalFound = 0;
            long totalTimeNs = 0;

            for (String preimage : preimages) {
                byte[] hash = SHA256Hasher.hash(preimage);
                String hex = SHA256Hasher.hex(hash);

                long t0 = System.nanoTime();
                String result = reader.search(hash);
                long t1 = System.nanoTime();
                totalTimeNs += (t1 - t0);

                if (result != null) totalFound++;

                if (PRINT_EACH_RESULT) {
                    System.out.printf("%s - %s - %s%n", preimage, hex,
                            result != null ? result : "NOT FOUND");
                }
            }

            double avgMs = totalTimeNs / (double) TEST_HASH_COUNT / 1_000_000.0;
            double throughput = totalFound / (totalTimeNs / 1_000_000_000.0);

            System.out.printf("%nResults:%n");
            System.out.printf("  Total hashes tested: %d%n", TEST_HASH_COUNT);
            System.out.printf("  Found: %d / %d (%.2f%%)%n", totalFound, TEST_HASH_COUNT,
                    (totalFound * 100.0 / TEST_HASH_COUNT));
            System.out.printf("  Average search time: %.4f ms%n", avgMs);
            System.out.printf("  Throughput (successful searches/sec): %.1f%n", throughput);
            if (totalFound == TEST_HASH_COUNT) {
                System.out.println("  ✓ 100% coverage confirmed.");
            }
        }
    }

    private static List<String> generateRandomPreimages(int count) {
        List<String> list = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < count; i++) {
            int len = rand.nextInt(MAX_LENGTH_FOR_TEST) + 1;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < len; j++) {
                sb.append(Config.ALPHABET.charAt(rand.nextInt(Config.BASE)));
            }
            list.add(sb.toString());
        }
        return list;
    }
}