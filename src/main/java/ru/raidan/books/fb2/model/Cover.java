package ru.raidan.books.fb2.model;

import lombok.Data;

import javax.annotation.Nullable;

@Data
public class Cover {
    String href;
    @Nullable
    Binary binary;
}
