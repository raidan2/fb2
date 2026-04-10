package ru.raidan.books.fb2;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.raidan.books.db.Utils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Fb2TextConverterTest {

    @ParameterizedTest
    @MethodSource("convertToText")
    void convertToText(String htmlResource, String expectResource) throws IOException {
        var html = Utils.readResource(htmlResource);
        var expect = Utils.readResource(expectResource);

        var actual = new Fb2TextConverter().convertToText(html);
        assertEquals(expect, actual);
    }

    static List<Arguments> convertToText() {
        return List.of(
                Arguments.of("fb2html/example-1.html", "fb2html/expect-1.txt"),
                Arguments.of("fb2html/example-2.html", "fb2html/expect-2.txt")
        );
    }
}