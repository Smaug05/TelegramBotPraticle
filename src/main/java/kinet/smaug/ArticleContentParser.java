package kinet.smaug;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Duration;
import java.util.List;

public final class ArticleContentParser {
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36";

    private boolean debugEnabled = true;
    private final StringBuilder dbg = new StringBuilder(4096);

    public void setDebugEnabled(boolean enabled){ this.debugEnabled = enabled; }
    public String getLastDebug(){ return dbg.toString(); }
    private void D(String line){ if (debugEnabled) dbg.append(line).append('\n'); }
    private void Dsel(Document d, String css){ if (!debugEnabled) return; Elements es = d.select(css); D("SEL["+css+"]: "+es.size()); }
    private static String idCls(Element e){
        if (e == null) return "<null>";
        String id = e.id().isBlank()? "" : ("#"+e.id());
        String cls = e.classNames().isEmpty()? "" : ("."+String.join(".", e.classNames()));
        return e.tagName()+id+cls;
    }

    public ArticleContent parse(String articleUrl) throws Exception {
        dbg.setLength(0);
        D("REQ: "+articleUrl);

        Connection.Response resp = Jsoup.connect(articleUrl)
                .userAgent(UA)
                .referrer("https://habr.com/")
                .header("Accept-Language", "ru,en;q=0.8")
                .timeout((int) Duration.ofSeconds(20).toMillis())
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .execute();

        int status = resp.statusCode();
        String ct = resp.contentType();
        D("HTTP: "+status+" | Content-Type: "+ct);
        try { D("Final URL: "+resp.url()); } catch (Exception ignore){}

        Document doc = resp.parse();
        D("HTML length: "+doc.outerHtml().length());

        if (status != 200) {
            D("Non-200 status, abort.");
            throw new IllegalStateException("HTTP " + status + " for " + articleUrl);
        }

        Dsel(doc, "#post-content-body, section#post-content-body");
        Dsel(doc, "div.tm-article-presenter__content, section.tm-article-presenter__content");
        Dsel(doc, "div.tm-article-body__content, section.tm-article-body__content");
        Dsel(doc, "div.article-formatted-body, div.article-formatted-body_version-2");
        Dsel(doc, "main article");
        Dsel(doc, "article[role=main]");
        Dsel(doc, "article");
        Dsel(doc, "[id*=post][id*=content][id*=body]");
        Dsel(doc, "div[class*=article][class*=content]");

        Element root = selectArticleBody(doc);
        if (root == null) {
            D("Primary selectors failed, using fallback scoring…");
            root = bestTextualCandidate(doc);
        }
        D("ROOT: "+idCls(root));

        if (root == null) {
            D("No candidate found.");
            throw new IllegalStateException("Body not found for " + articleUrl);
        }

        root.select("script,style,noscript,header,footer,nav,menu,aside").remove();

        List<String> imgs = root.select("img[src]").stream()
                .map(el -> el.absUrl("src"))
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .limit(10)
                .toList();

        String md = HtmlToMarkdownV2.convert(root, articleUrl);
        D("Markdown length: "+md.length());
        D("Preview: "+(md.length()>200? md.substring(0,200)+"…" : md));
        return new ArticleContent(articleUrl, md, imgs);
    }

    private Element selectArticleBody(Document doc) {
        Element e;
        e = doc.selectFirst("#post-content-body, section#post-content-body"); if (e != null) return e;
        e = doc.selectFirst("div.tm-article-presenter__content, section.tm-article-presenter__content"); if (e != null) return e;
        e = doc.selectFirst("div.tm-article-body__content, section.tm-article-body__content"); if (e != null) return e;
        e = doc.selectFirst("div.article-formatted-body, div.article-formatted-body_version-2"); if (e != null) return e;
        e = doc.selectFirst("[id*=post][id*=content][id*=body]"); if (e != null) return e;
        e = doc.selectFirst("div[class*=article][class*=content]"); if (e != null) return e;
        e = doc.selectFirst("main article, article[role=main], article"); if (e != null) return e;
        return null;
    }

    private Element bestTextualCandidate(Document doc) {
        Elements candidates = doc.select("main, article, section, div");
        Element best = null;
        int bestScore = 0;
        for (Element c : candidates) {
            int p = c.select("p").size();
            int pre = c.select("pre, code").size();
            int len = c.text().length();
            int score = len + 200 * p + 400 * pre;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        D("Fallback chosen: "+idCls(best)+" score="+bestScore);
        return best;
    }

    public record ArticleContent(String url, String markdown, java.util.List<String> images) {}
}
