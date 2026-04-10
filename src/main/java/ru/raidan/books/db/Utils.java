package ru.raidan.books.db;

import java.io.IOException;

public class Utils {
    private Utils() {
    }

    public static String readResource(String resourceName) throws IOException {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new RuntimeException("Unable to load resource [" + resourceName + "]");
            }
            return new String(stream.readAllBytes());
        }
    }
}
