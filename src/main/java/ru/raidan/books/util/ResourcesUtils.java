package ru.raidan.books.util;

import java.net.URL;

public class ResourcesUtils {

    private ResourcesUtils() {

    }

    public static URL getResource(String resourcePath) {
        var resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Unable to find resource " + resourcePath);
        }
        return resource;
    }
}
