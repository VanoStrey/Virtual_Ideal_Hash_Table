package org.examle.vht;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SHA256Hasher {
    private static final ThreadLocal<MessageDigest> DIGEST =
        ThreadLocal.withInitial(() -> {
            try { return MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
        });

    private static final ThreadLocal<byte[]> ASCII_BUF =
        ThreadLocal.withInitial(() -> new byte[Config.MAX_STRING_LENGTH]);

    public static byte[] hash(String input) {
        byte[] buf = ASCII_BUF.get();
        int len = input.length();
        for (int i = 0; i < len; i++) buf[i] = (byte) input.charAt(i);
        MessageDigest md = DIGEST.get();
        md.reset();
        md.update(buf, 0, len);
        return md.digest();
    }

    public static int slotIndex(byte[] sha256) {
        int raw = ((sha256[28] & 0xFF) << 24) |
                  ((sha256[29] & 0xFF) << 16) |
                  ((sha256[30] & 0xFF) << 8)  |
                  (sha256[31] & 0xFF);
        return raw & 0x0FFFFFFF; // 28 бит
    }

    public static String hex(byte[] hash) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}