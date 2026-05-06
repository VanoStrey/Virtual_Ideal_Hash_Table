package org.examle.vht;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("1 - Build table\n2 - Search hash");
        Scanner sc = new Scanner(System.in);
        int choice = sc.nextInt();
        sc.nextLine();
        if (choice == 1) {
            OptimalVHTBuilder.main(args);
        } else if (choice == 2) {
            System.out.print("Enter SHA-256 hex: ");
            String hex = sc.nextLine().trim().replaceAll("\\s", "");
            if (hex.length() != 64) {
                System.out.println("Invalid hash length");
                return;
            }
            byte[] target = hexToBytes(hex);
            try (OptimalVHTReader reader = new OptimalVHTReader(Config.PRIMARY_FILE)) {
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