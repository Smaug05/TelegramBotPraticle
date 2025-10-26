package kinet.smaug.test;


import kinet.smaug.ArticleCard;
import kinet.smaug.HabrParser;
import org.testng.annotations.Test;
import java.util.List;

import static org.testng.Assert.*;

public class ArticleCardFieldsTest {
    @Test
    void shouldContainMandatoryFields() throws Exception {
        var parser = new HabrParser();
        List<ArticleCard> list = parser.fetchPage(1);

        int n = Math.min(5, list.size());
        int withImage = 0;
        for (int i = 0; i < n; i++){
            var c = list.get(i);
            assertNotNull(c.title());
            assertFalse(c.title().isBlank(), "title пуст");
            assertNotNull(c.summary());
            assertFalse(c.summary().isBlank(), "summary пуст");
            if (c.imageUrl() != null && !c.imageUrl().isBlank()) withImage++;
        }
        assertTrue(withImage >= 1, "Ожидали ≥1 карточку с изображением среди первых " + n);
    }
}