package ru.raidan.books.fb2;

import java.util.zip.ZipEntry;

public interface DocumentFilter {

    boolean canParse(String archiveFile, ZipEntry entry);
}
