package ru.raidan.books.fb2.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class TitleInfo {

    List<Author> authors = new ArrayList<>();
    Map<String, Cover> covers = new HashMap<>();
    String bookTitle;
    String annotation;

    public void addAuthor(Author author) {
        this.authors.add(author);
    }

    public void addCover(Cover cover) {
        this.covers.put(cover.getHref(), cover);
    }

    public Cover getCover(Binary binary) {
        return this.covers.get("#" + binary.getId());
    }

}
