package org.examle.vht;

public final class Config {
    public static final String ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final int BASE = ALPHABET.length(); // 62
    public static final String STORAGE_ALPHABET = " " + ALPHABET;
    public static final int STORAGE_BASE = STORAGE_ALPHABET.length(); // 63
    public static final int MAX_STRING_LENGTH = 5;

    public static final long TOTAL_PREIMAGES = 931_151_402L;

    public static final int SLOT_INDEX_BITS = 28;
    public static final long TOTAL_SLOTS = 1L << SLOT_INDEX_BITS; // 268_435_456
    public static final int CELLS_PER_SLOT = 8;
    public static final int CELL_BYTES = 4;
    public static final int SLOT_BYTES = CELLS_PER_SLOT * CELL_BYTES; // 32
    public static final long FILE_SIZE = TOTAL_SLOTS * SLOT_BYTES; // 8 GiB

    public static final String PRIMARY_FILE = "primary_table.bin";
}