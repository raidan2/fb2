package ru.raidan.books.db;

import ru.raidan.books.db.model.library.tables.pojos.AuthorToBook;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.db.model.library.tables.pojos.BookToGenre;

import java.util.List;

public record BookWithAuthorsAndGenres(Book book, List<AuthorToBook> authors, List<BookToGenre> genres) {
}
