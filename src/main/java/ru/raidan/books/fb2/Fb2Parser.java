package ru.raidan.books.fb2;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ru.raidan.books.fb2.model.FictionBook;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;

public class Fb2Parser {

    private static final SAXParserFactory FACTORY = SAXParserFactory.newInstance();

    public static FictionBook parse(InputSource source) throws ParserConfigurationException, SAXException, IOException {
        var handler = new Fb2Handler();
        FACTORY.newSAXParser().parse(source, handler);
        return handler.getFictionBook();
    }

}
