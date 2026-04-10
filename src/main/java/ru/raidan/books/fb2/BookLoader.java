package ru.raidan.books.fb2;

import ru.raidan.books.fb2.model.FictionBook;

public interface BookLoader {
    FictionBook loadBook(int bookId);
}
