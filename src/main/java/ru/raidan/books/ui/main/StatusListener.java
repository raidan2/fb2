package ru.raidan.books.ui.main;

public interface StatusListener {

    void setLibrary(String database);

    void setStatus(String text);

    void setReady();
}
