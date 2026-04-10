package ru.raidan.books.fb2;

import ru.raidan.books.fb2.model.FictionBook;

import java.io.IOException;
import java.util.zip.ZipEntry;

public interface DocumentConsumer {
    void onDocument(ZipEntry entry, FictionBook document) throws IOException;
}
