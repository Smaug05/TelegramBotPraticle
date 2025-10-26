package kinet.smaug.test;
import kinet.smaug.ArticleCard;
import kinet.smaug.ArticleContentParser;
import kinet.smaug.HabrParser;
import org.testng.annotations.Test;
import java.util.List;

import static org.testng.Assert.*;

public class ArticleContentParserRealPageTest {

    @Test
    public void shouldParseArticle_959788() throws Exception {
        String url = "https://habr.com/ru/articles/959788/";
        var parser = new ArticleContentParser();
        var content = parser.parse(url);
        assertNotNull(content);
        assertNotNull(content.markdown());
        assertTrue(content.markdown().length() > 500, "Слишком короткий markdown");
        assertTrue(content.markdown().contains("Марс") || content.markdown().contains("Марсу"),
                "Не нашли ключевые слова в тексте");
    }
}
