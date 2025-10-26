package kinet.smaug;

import java.util.ArrayList;
import java.util.List;

public final class MessageSplitter {
    private MessageSplitter(){}

    public static List<String> splitMarkdownV2(String text, int limit){
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()){ out.add(""); return out; }

        int i = 0;
        while (i < text.length()){
            int end = Math.min(text.length(), i + limit);
            int cut = lastParagraphBreak(text, i, end);
            if (cut <= i) cut = end;
            out.add(text.substring(i, cut));
            i = cut;
        }
        return out;
    }

    private static int lastParagraphBreak(String s, int start, int end){
        int idx = s.lastIndexOf("\n\n", end - 1);
        return (idx >= start && idx - start > 256) ? idx : -1;
    }
}
