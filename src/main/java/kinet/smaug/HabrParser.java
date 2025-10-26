package kinet.smaug;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HabrParser {
    private static final String BASE = "https://habr.com/ru/articles/";
    private static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    public List<ArticleCard> fetchPage(int page) throws Exception {
        String url = page <= 1 ? BASE : (BASE + "page" + page + "/");
        Document doc = Jsoup.connect(url)
                .userAgent(UA)
                .referrer("https://www.google.com")
                .timeout((int) Duration.ofSeconds(15).toMillis())
                .get();

        Elements items = new Elements();
        items.addAll(doc.select("article.tm-articles-list__item"));

        if (items.isEmpty()) items = doc.select("article:has(a[href^=/ru/articles/])");

        List<ArticleCard> out = new ArrayList<>(items.size());
        for (Element it : items) {
            Element a = it.selectFirst("a.tm-article-snippet__title-link");
            if (a == null) a = it.selectFirst("a[href^=/ru/articles/]");
            if (a == null) continue;

            String title = a.text().strip();
            String href = a.absUrl("href");

            String summary = findSummary(it);
            String image = findImage(it);

            if (!href.isBlank() && !title.isBlank()) {
                out.add(new ArticleCard(title, href, image, summary));
            }
        }
        return out;
    }

    private String findSummary(Element it) {
        Element p = it.selectFirst("div.article-formatted-body, div.tm-article-snippet, div[class*=snippet] p, p");
        String txt = p != null ? p.text() : it.text();
        txt = txt.replace("Читать далее", "").strip();
        if (txt.length() > 400) txt = txt.substring(0, 397) + "...";
        return txt;
    }

    private String findImage(Element it) {
        Element img = it.selectFirst("img");
        if (img == null) return null;
        String[] attrs = {"src", "data-src", "data-srcset", "srcset"};
        for (String a : attrs) {
            String v = img.attr(a);
            if (v != null && !v.isBlank()) {
                if (a.endsWith("srcset")) {
                    String first = v.split("\\s+")[0];
                    return img.absUrl(first.isBlank() ? "src" : "src").isBlank() ? first : img.absUrl(first);
                }
                String abs = img.absUrl(a);
                return abs.isBlank() ? v : abs;
            }
        }
        return null;
    }
}
