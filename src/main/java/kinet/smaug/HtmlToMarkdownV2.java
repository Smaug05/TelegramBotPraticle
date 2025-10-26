package kinet.smaug;

import org.jsoup.nodes.*;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

public final class HtmlToMarkdownV2 {
    private HtmlToMarkdownV2(){}

    public static String convert(Element root, String sourceUrl){
        StringBuilder out = new StringBuilder(4096);
        NodeTraversor.traverse(new Visitor(out), root);
        String text = out.toString().replace("\r", "");
        text += "\n\n" + Markdown.escapeV2("Источник: ") +
                "[" + Markdown.escapeV2(sourceUrl) + "](" + Markdown.escapeUrl(sourceUrl) + ")";
        return text.trim();
    }

    private static final class Visitor implements NodeVisitor {
        private final StringBuilder sb;
        private boolean inPre = false;
        private int boldDepth = 0, emDepth = 0, codeInlineDepth = 0;

        Visitor(StringBuilder sb){ this.sb = sb; }

        @Override public void head(Node node, int depth) {
            if (node instanceof Element el){
                switch (el.normalName()){
                    case "h1","h2","h3","h4","h5","h6" -> sb.append("\n\n*");
                    case "p" -> sb.append("\n\n");
                    case "ul","ol" -> sb.append("\n");
                    case "li" -> sb.append("\n• ");
                    case "strong","b" -> { sb.append("*"); boldDepth++; }
                    case "em","i" -> { sb.append("_"); emDepth++; }
                    case "a" -> {
                        String href = el.absUrl("href");
                        String label = el.text().isBlank() ? href : el.text();
                        sb.append(" [").append(Markdown.escapeV2(label)).append("](")
                                .append(Markdown.escapeUrl(href)).append(") ");
                    }
                    case "code" -> {
                        if (!inPre){ sb.append("`"); codeInlineDepth++; }
                    }
                    case "pre" -> {
                        inPre = true;
                        String lang = guessLang(el.className());
                        sb.append("\n\n```").append(lang).append("\n")
                                .append(Markdown.escapeCode(el.text()))
                                .append("\n```\n");
                    }
                    case "br" -> sb.append("\n");
                    case "img" -> {
                        String src = el.absUrl("src");
                        if (!src.isBlank()){
                            sb.append("\n").append(Markdown.escapeV2("[изображение]"))
                                    .append("(").append(Markdown.escapeUrl(src)).append(")\n");
                        }
                    }
                }
            } else if (node instanceof TextNode tn) {
                if (!inPre) sb.append(Markdown.escapeV2(tn.text()));
            }
        }

        @Override public void tail(Node node, int depth) {
            if (node instanceof Element el){
                switch (el.normalName()){
                    case "h1","h2","h3","h4","h5","h6" -> sb.append("*\n");
                    case "strong","b" -> { if (boldDepth>0) { sb.append("*"); boldDepth--; } }
                    case "em","i"      -> { if (emDepth>0)   { sb.append("_"); emDepth--; } }
                    case "code"        -> { if (!inPre && codeInlineDepth>0) { sb.append("`"); codeInlineDepth--; } }
                    case "pre"         -> inPre = false;
                }
            }
        }

        private String guessLang(String cls){
            if (cls == null) return "";
            String s = cls.toLowerCase();
            if (s.contains("java")) return "java";
            if (s.contains("xml")) return "xml";
            if (s.contains("json")) return "json";
            if (s.contains("kotlin")) return "kotlin";
            if (s.contains("python")) return "python";
            return "";
        }
    }
}
