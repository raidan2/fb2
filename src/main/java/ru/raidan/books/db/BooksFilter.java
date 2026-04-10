package ru.raidan.books.db;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value
@Builder
public class BooksFilter {
    @Nullable
    Integer authorId;
    @Nullable
    String lang;
    @Nullable
    String title;
    @Nullable
    @Singular
    List<Integer> bookIds;
    @Nullable
    @Singular
    List<Integer> genres;
    int limit;

    public static class BooksFilterBuilder {
        {
            limit = 10000;
        }
    }
}
