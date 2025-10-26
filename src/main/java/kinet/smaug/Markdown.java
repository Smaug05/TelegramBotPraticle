package kinet.smaug;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Markdown {
    private Markdown(){}

    public static String escapeV2(String s){
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char ch : s.toCharArray()){
            switch (ch){
                case '_','*','[',']','(',')','~','`','>','#','+','-','=','|','{','}','.','!' -> sb.append('\\').append(ch);
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String escapeCode(String s){
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("`", "\\`");
    }

    // для ссылок в круглых скобках MarkdownV2 — безопасный URL-encode
    public static String escapeUrl(String url){
        if (url == null) return "";
        return url.replace("(", "%28").replace(")", "%29").replace(" ", "%20");
    }
}
