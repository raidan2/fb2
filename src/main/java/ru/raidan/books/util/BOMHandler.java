package ru.raidan.books.util;

import io.vavr.control.Either;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.Function;

public class BOMHandler {

    private static final int MAX_SIZE = 4;

    private static final byte[] BOM_UTF8 = HexFormat.of().parseHex("EFBBBF");
    private static final byte[] BOM_UTF16_BE = HexFormat.of().parseHex("FEFF");
    private static final byte[] BOM_UTF16_LE = HexFormat.of().parseHex("FFFE");
    private static final byte[] BOM_UTF32_BE = HexFormat.of().parseHex("0000FEFF");
    private static final byte[] BOM_UTF32_LE = HexFormat.of().parseHex("FFFE0000");

    public static InputSource xmlFromInputStream(InputStream stream) throws IOException {
        return fromInputStream(stream)
                .fold(InputSource::new, InputSource::new);
    }

    public static InputStreamReader rawFromInputStream(InputStream stream) throws IOException {
        return fromInputStream(stream)
                .fold(Function.identity(), InputStreamReader::new);
    }

    public static Either<InputStreamReader, InputStream> fromInputStream(InputStream stream) throws IOException {
        var pb = new PushbackInputStream(stream, MAX_SIZE);

        // TODO: support multiple BOMs
        var prefix = pb.readNBytes(MAX_SIZE);
        if (matches(prefix, BOM_UTF8, pb)) {
            return Either.left(new InputStreamReader(pb, StandardCharsets.UTF_8));
        } else if (matches(prefix, BOM_UTF16_BE, pb)) {
            return Either.left(new InputStreamReader(pb, StandardCharsets.UTF_16BE));
        } else if (matches(prefix, BOM_UTF16_LE, pb)) {
            return Either.left(new InputStreamReader(pb, StandardCharsets.UTF_16LE));
        } else if (matches(prefix, BOM_UTF32_BE, pb)) {
            return Either.left(new InputStreamReader(pb, Charset.forName("UTF-32BE")));
        } else if (matches(prefix, BOM_UTF32_LE, pb)) {
            return Either.left(new InputStreamReader(pb, Charset.forName("UTF-32LE")));
        } else {
            pb.unread(prefix);
            return Either.right(pb);
        }
    }

    private static boolean matches(byte[] actualPrefix, byte[] expectPrefix, PushbackInputStream pb) throws IOException {
        int len = expectPrefix.length;
        if (Arrays.equals(actualPrefix, 0, len, expectPrefix, 0, len)) {
            pb.unread(actualPrefix, expectPrefix.length, actualPrefix.length - expectPrefix.length);
            return true;
        }
        return false;
    }
}
