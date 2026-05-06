package org.examle.vht;

public final class PreimageCodec {

    private static final long[] POW = new long[Config.MAX_STRING_LENGTH + 1];
    static {
        POW[0] = 1;
        for (int i = 1; i <= Config.MAX_STRING_LENGTH; i++) {
            POW[i] = POW[i - 1] * Config.BASE;
        }
    }

    private static final long[] OFFSET = new long[Config.MAX_STRING_LENGTH + 2];
    static {
        OFFSET[1] = 0;
        for (int len = 2; len <= Config.MAX_STRING_LENGTH + 1; len++) {
            OFFSET[len] = OFFSET[len - 1] + POW[len - 1];
        }
    }

    public static String indexToPreimage(int idx) {
        long remaining = idx & 0xFFFFFFFFL;
        int len = 1;
        while (len <= Config.MAX_STRING_LENGTH && remaining >= OFFSET[len + 1]) {
            len++;
        }
        long local = remaining - OFFSET[len];
        char[] buf = new char[len];
        for (int i = len - 1; i >= 0; i--) {
            buf[i] = Config.ALPHABET.charAt((int) (local % Config.BASE));
            local /= Config.BASE;
        }
        return new String(buf);
    }

    public static byte[] pack(String preimage) {
        if (preimage.length() > Config.MAX_STRING_LENGTH)
            throw new IllegalArgumentException("String too long");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Config.MAX_STRING_LENGTH - preimage.length(); i++) {
            sb.append(' ');
        }
        sb.append(preimage);
        String padded = sb.toString();
        long val = 0;
        for (int i = 0; i < padded.length(); i++) {
            char c = padded.charAt(i);
            int digit = c == ' ' ? 0 : Config.ALPHABET.indexOf(c) + 1;
            val = val * Config.STORAGE_BASE + digit;
        }
        byte[] out = new byte[Config.CELL_BYTES];
        out[0] = (byte) (val >> 24);
        out[1] = (byte) (val >> 16);
        out[2] = (byte) (val >> 8);
        out[3] = (byte) val;
        return out;
    }

    public static String unpack(byte[] packed) {
        long val = ((packed[0] & 0xFFL) << 24) |
                   ((packed[1] & 0xFFL) << 16) |
                   ((packed[2] & 0xFFL) << 8)  |
                   (packed[3] & 0xFFL);
        char[] buf = new char[Config.MAX_STRING_LENGTH];
        for (int i = Config.MAX_STRING_LENGTH - 1; i >= 0; i--) {
            int digit = (int) (val % Config.STORAGE_BASE);
            buf[i] = digit == 0 ? ' ' : Config.STORAGE_ALPHABET.charAt(digit);
            val /= Config.STORAGE_BASE;
        }
        String padded = new String(buf);
        int start = 0;
        while (start < padded.length() && padded.charAt(start) == ' ') start++;
        return padded.substring(start);
    }
}