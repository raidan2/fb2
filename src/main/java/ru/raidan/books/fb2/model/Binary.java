package ru.raidan.books.fb2.model;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Base64;

@Slf4j
@Data
public class Binary {
    String id;
    String contentType;

    @ToString.Exclude
    String content;

    @Nullable
    public byte[] toBytes() {
        try {
            return Base64.getDecoder().decode(content.replace("\n", ""));
        } catch (Exception e) {
            log.error("Unable to convert Base64 encoded value to bytes", e);
            return null;
        }
    }
}
