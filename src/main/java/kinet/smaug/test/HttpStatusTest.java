package kinet.smaug.test;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class HttpStatusTest {

    private static final String BASE = "https://habr.com/ru/articles/";

    @Test
    public void basePageShouldBe200() throws Exception {
        Connection.Response r = Jsoup.connect(BASE)
                .followRedirects(true)
                .ignoreHttpErrors(true) // ← на всякий случай, но тут будет 200
                .execute();
        System.out.println("STATUS " + r.statusCode() + " for " + BASE);
        assertEquals(r.statusCode(), 200);
    }

    @Test
    public void nonExistingPageShouldBe404or200() throws Exception {
        String bad = BASE + "123ыфыкцу/";
        Connection.Response r = Jsoup.connect(bad)
                .followRedirects(true)
                .ignoreHttpErrors(true) // ← ключевая правка
                .execute();
        System.out.println("STATUS " + r.statusCode() + " for " + bad);
        assertTrue(r.statusCode() == 404 || r.statusCode() == 200,
                "Ожидаем 404 (или 200 от кастомной 404-страницы), получили " + r.statusCode());
    }
}