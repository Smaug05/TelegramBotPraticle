package kinet.smaug.test;


import kinet.smaug.ArticleCard;
import kinet.smaug.HabrParser;
import org.testng.annotations.Test;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;


public class HabrParserListTest {
    @Test
    void shouldFetchNonEmptyList() throws Exception {
        var parser = new HabrParser();
        List<ArticleCard> list = parser.fetchPage(1);
        assertNotNull(list);
        assertFalse(list.isEmpty(), "Список статей пуст");
    }
}