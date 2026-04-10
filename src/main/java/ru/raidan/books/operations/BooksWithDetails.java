package ru.raidan.books.operations;

import ru.raidan.books.db.model.cache.tables.pojos.BookCache;
import ru.raidan.books.db.model.library.tables.pojos.Book;

public record BooksWithDetails(Book book, BookCache bookCache) {
}
