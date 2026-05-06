package org.examle.vht;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("1 - Build table");
        System.out.println("2 - Search hash (interactive)");
        System.out.println("3 - Benchmark");
        System.out.print("Choice: ");
        int choice = sc.nextInt();
        sc.nextLine();
        if (choice == 1) {
            OptimalVHTBuilder.main(args);
        } else if (choice == 2) {
            interactiveSearch(sc);
        } else if (choice == 3) {
            OptimalVHTBenchmark.main(args);
        }
    }

    private static void interactiveSearch(Scanner sc) throws Exception {
        System.out.println("=== Interactive search ===");
        System.out.println("Enter SHA-256 hex (or 'exit' to quit):");
        try (OptimalVHTReader reader = new OptimalVHTReader(Config.PRIMARY_FILE)) {
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.equalsIgnoreCase("exit")) {
                    System.out.println("Exiting.");
                    break;
                }
                String hex = line.replaceAll("\\s", "");
                if (hex.length() != 64) {
                    System.out.println("Invalid hash length (must be 64 hex chars)");
                    continue;
                }
                byte[] target = hexToBytes(hex);
                long t0 = System.nanoTime();
                String result = reader.search(target);
                long t1 = System.nanoTime();
                System.out.printf("%s '%s' in %.3f ms%n",
                        result != null ? "Found:" : "Not found", result, (t1 - t0) / 1e6);
            }
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] b = new byte[32];
        for (int i = 0; i < 32; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}