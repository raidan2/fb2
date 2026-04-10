package ru.raidan.books.fb2;

import lombok.Getter;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import ru.raidan.books.fb2.model.Author;
import ru.raidan.books.fb2.model.Binary;
import ru.raidan.books.fb2.model.Cover;
import ru.raidan.books.fb2.model.Description;
import ru.raidan.books.fb2.model.FictionBook;
import ru.raidan.books.fb2.model.TitleInfo;

import javax.annotation.Nullable;

public class Fb2Handler extends DefaultHandler {
    private final StringBuilder buffer = new StringBuilder();

    @Getter
    private FictionBook fictionBook;
    private Description description;
    private TitleInfo title;
    private Author author;
    private Cover cover;

    private State state;
    private boolean read;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (qName) {
            case "FictionBook" -> {
                if (state(null, State.FICTION_BOOK)) {
                    if (fictionBook != null) {
                        throw new IllegalStateException("Found more than 1 FictionBook");
                    }
                    fictionBook = new FictionBook();
                }
            }
            case "description" -> {
                if (state(State.FICTION_BOOK, State.DESCRIPTION)) {
                    description = new Description();
                    fictionBook.setDescription(description);
                }
            }
            case "title-info" -> {
                if (state(State.DESCRIPTION, State.TITLE_INFO)) {
                    title = new TitleInfo();
                    description.setTitleInfo(title);
                }
            }
            case "author" -> {
                if (state(State.TITLE_INFO, State.AUTHOR)) {
                    author = new Author();
                    title.addAuthor(author);
                }
            }
            case "first-name", "last-name" -> {
                if (state == State.AUTHOR) {
                    startReading();
                }
            }
            case "book-title", "annotation" -> {
                if (state == State.TITLE_INFO) {
                    startReading();
                }
            }
            case "coverpage" -> state(State.TITLE_INFO, State.COVERPAGE);
            case "image" -> {
                if (state == State.COVERPAGE) {
                    var href = attributes.getValue("l:href");
                    if (href != null) {
                        var cover = new Cover();
                        cover.setHref(href);
                        title.addCover(cover);
                    }
                }
            }
            case "body" -> state(State.FICTION_BOOK, State.BODY);
            case "binary" -> {
                if (state(State.FICTION_BOOK, State.BINARY)) {
                    var binary = new Binary();
                    binary.setId(attributes.getValue("id"));

                    cover = fictionBook.getDescription().getTitleInfo().getCover(binary);
                    if (cover != null) {
                        binary.setContentType(attributes.getValue("content-type"));
                        cover.setBinary(binary);
                        startReading();
                    }
                }
            }
            default -> {
                if (read) {
                    buffer.append("<").append(qName).append(">");
                }
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (read) {
            buffer.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (qName) {
            case "FictionBook" -> state(State.FICTION_BOOK, null);
            case "description" -> {
                if (state(State.DESCRIPTION, State.FICTION_BOOK)) {
                    description = null;
                }
            }
            case "title-info" -> {
                if (state(State.TITLE_INFO, State.DESCRIPTION)) {
                    title = null;
                }
            }
            case "author" -> {
                if (state(State.AUTHOR, State.TITLE_INFO)) {
                    author = null;
                }
            }
            case "first-name" -> {
                if (state == State.AUTHOR) {
                    author.setFirstName(stopReading());
                }
            }
            case "last-name" -> {
                if (state == State.AUTHOR) {
                    author.setLastName(stopReading());
                }
            }
            case "book-title" -> {
                if (state == State.TITLE_INFO) {
                    title.setBookTitle(stopReading());
                }
            }
            case "annotation" -> {
                if (state == State.TITLE_INFO) {
                    title.setAnnotation(stopReading());
                }
            }
            case "coverpage" -> state(State.COVERPAGE, State.TITLE_INFO);
            case "body" -> state(State.BODY, State.FICTION_BOOK);
            case "binary" -> {
                if (state(State.BINARY, State.FICTION_BOOK)) {
                    if (cover != null) {
                        if (cover.getBinary() != null) {
                            cover.getBinary().setContent(stopReading());
                        }
                        cover = null;
                    }
                }
            }
            default -> {
                if (read) {
                    buffer.append("</").append(qName).append(">");
                }
            }
        }
    }

    private boolean state(@Nullable State expect, State toSet) {
        if (this.state == expect) {
            this.state = toSet;
            return true;
        } else {
            return false;
        }
    }

    private void startReading() {
        this.buffer.setLength(0);
        this.read = true;
    }

    private String stopReading() {
        this.read = false;
        return this.buffer.toString();
    }

    enum State {
        FICTION_BOOK,
        DESCRIPTION,
        TITLE_INFO,
        AUTHOR,
        COVERPAGE,
        BODY,
        BINARY
    }

}
