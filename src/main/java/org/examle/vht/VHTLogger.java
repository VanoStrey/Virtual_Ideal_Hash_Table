package org.examle.vht;

import java.io.*;
import java.util.logging.*;

public final class VHTLogger {
    public static final Logger LOGGER = Logger.getLogger("VHT");

    static {
        LOGGER.setUseParentHandlers(false);
        try {
            Handler handler = new StreamHandler(System.out, new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%1$tH:%1$tM:%1$tS] %2$-7s %3$s%n",
                            record.getMillis(), record.getLevel().getName(), record.getMessage());
                }
            }) {
                @Override
                public synchronized void publish(LogRecord record) {
                    super.publish(record);
                    flush();
                }
            };
            handler.setLevel(Level.ALL);
            LOGGER.addHandler(handler);
        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }
}