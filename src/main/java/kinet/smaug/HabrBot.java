package kinet.smaug;

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HabrBot implements LongPollingSingleThreadUpdateConsumer {
    private static final int TG_MSG_LIMIT = 4096;     // лимит текста сообщений
    private static final int TG_CAPTION_SAFE = 900;   // держим caption <1024 (запас под теги/сущности)

    private final TelegramClient tg;
    private final HabrParser parser = new HabrParser();
    private final ArticleContentParser contentParser = new ArticleContentParser();

    private final Map<Long, FeedState> state = new ConcurrentHashMap<>();

    // короткие токены в callback_data (<=64 байт) -> URL
    private final Map<String, String> cbMap = new ConcurrentHashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    // пул для тяжёлых задач, чтобы не блокировать consume
    private final ExecutorService workers = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> { Thread t = new Thread(r, "habrbot-worker"); t.setDaemon(true); return t; });

    public HabrBot(TelegramClient telegramClient) {
        this.tg = telegramClient;
    }

    @Override
    public void consume(Update upd) {
        try {
            if (upd.hasCallbackQuery()) {
                onCallback(upd);
                return;
            }
            if (upd.hasMessage() && upd.getMessage().hasText()) {
                onMessage(upd);
            }
        } catch (Exception e) {
            System.err.println("[consume] " + e.getMessage());
        }
    }

    private void onMessage(Update upd) throws Exception {
        var msg = upd.getMessage();
        long chatId = msg.getChatId();
        String text = msg.getText().trim();

        state.putIfAbsent(chatId, new FeedState());

        if ("/start".equals(text)) {
            String welcome = """
                    Привет! Я парсю ленту Habr и показываю карточки статей.
                    Нажимай «Лента», «◀️ Назад» и «▶️ Вперёд».
                    """;
            sendText(chatId, welcome, replyKb());
            return;
        }

        switch (text) {
            case "📰 Лента", "▶️ Вперёд" -> showPage(chatId, +1);
            case "◀️ Назад" -> showPage(chatId, -1);
            default -> sendText(chatId, "Команды: /start, «📰 Лента», «◀️ Назад», «▶️ Вперёд»", replyKb());
        }
    }

    private void onCallback(Update upd) {
        var cq = upd.getCallbackQuery();
        Long chatId = cq.getMessage() != null ? cq.getMessage().getChatId() : null;
        String data = cq.getData();
        System.out.println("[CQ] from=" + (cq.getFrom()!=null?cq.getFrom().getId():null)
                + " chat=" + chatId + " data=" + data);

        try {
            if (data != null && data.startsWith("f:")) {
                safeAnswerOk(cq.getId(), "Открываю…"); // гасим «часики» сразу
                String token = data.substring(2);
                String url = cbMap.remove(token);
                if (url == null) {
                    safeAnswerAlert(cq.getId(), "Кнопка устарела");
                    return;
                }
                if (chatId == null) return;

                workers.submit(() -> {
                    try {
                        var content = contentParser.parse(url);
                        sendArticleWithMedia(chatId, content);
                    } catch (Exception ex) {
                        System.err.println("[full] " + ex.getMessage());
                        try { safeSendPlainLink(chatId, url); } catch (Exception ignore) {}
                    }
                });
                return;
            }

            switch (String.valueOf(data)) {
                case "page:prev" -> { if (chatId != null) showPage(chatId, -1); safeAnswerOk(cq.getId(), "◀️"); }
                case "page:next" -> { if (chatId != null) showPage(chatId, +1); safeAnswerOk(cq.getId(), "▶️"); }
                case "noop"      -> { safeAnswerOk(cq.getId(), null); }
                default          -> { safeAnswerAlert(cq.getId(), "Неизвестная команда"); }
            }
        } catch (Exception e) {
            System.err.println("[onCallback] " + e.getMessage());
            try { safeAnswerOk(cq.getId(), null); } catch (Exception ignore) {}
        }
    }

    // ---- лента/карточки ----

    private void showPage(long chatId, int delta) throws Exception {
        var st = state.computeIfAbsent(chatId, k -> new FeedState());
        int next = Math.max(1, st.page + (st.page == 0 ? 1 : delta));
        var cards = parser.fetchPage(next);
        if (cards.isEmpty()) {
            sendText(chatId, "Пусто. Попробуй ещё раз позже.", replyKb());
            return;
        }
        st.page = next;

        for (int i = 0; i < Math.min(3, cards.size()); i++) {
            sendCard(chatId, cards.get(i), st.page);
        }
    }

    private void sendCard(long chatId, ArticleCard c, int page) throws TelegramApiException {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder().text("🔗 Открыть").url(c.url()).build());
        row1.add(InlineKeyboardButton.builder().text("📖 Показать полностью").callbackData(putUrlAndGetToken(c.url())).build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder().text("◀️").callbackData("page:prev").build());
        row2.add(InlineKeyboardButton.builder().text("Стр. " + page).callbackData("noop").build());
        row2.add(InlineKeyboardButton.builder().text("▶️").callbackData("page:next").build());

        var kb = InlineKeyboardMarkup.builder().keyboardRow(row1).keyboardRow(row2).build();

        // КАПШН ДЕЛАЕМ ЧЕРЕЗ HTML (безопаснее, чем MarkdownV2 в фото)
        String captionHtml = buildCaptionHtml(c.title(), c.summary());

        if (c.imageUrl() != null && !c.imageUrl().isBlank()) {
            var photo = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(c.imageUrl()))
                    .caption(captionHtml)
                    .parseMode(ParseMode.HTML)
                    .replyMarkup(kb)
                    .build();
            tg.execute(photo);
        } else {
            // без картинки — обычное сообщение в MarkdownV2
            tg.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("*" + Markdown.escapeV2(c.title()) + "*\n" + Markdown.escapeV2(c.summary()) + "\n" + Markdown.escapeV2(c.url()))
                    .parseMode(ParseMode.MARKDOWNV2)
                    .replyMarkup(kb)
                    .disableWebPagePreview(false)
                    .build());
        }
    }

    // ---- отправка полной статьи ----

    private void sendArticleWithMedia(long chatId, ArticleContentParser.ArticleContent content) throws TelegramApiException {
        List<String> imgs = content.images(); // может быть null
        List<String> parts = splitMarkdownV2Safely(content.markdown());

        System.out.println("[full] imgs=" + (imgs == null ? 0 : imgs.size()) +
                " firstPart=" + (parts.isEmpty() ? 0 : parts.get(0).length()));

        if (imgs != null && !imgs.isEmpty()) {
            // caption для фото — тоже HTML, а не MarkdownV2
            String firstPlain = parts.isEmpty() ? "" : stripMarkdownV2(parts.get(0));
            String captionHtml = trimHtmlCaption(htmlEscape(firstPlain), TG_CAPTION_SAFE);

            if (imgs.size() == 1) {
                tg.execute(SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(imgs.get(0)))
                        .caption(captionHtml)
                        .parseMode(ParseMode.HTML)
                        .build());
                if (parts.size() > 1) sendParts(chatId, parts.subList(1, parts.size()));
            } else {
                // альбом 2..10
                List<InputMedia> media = new ArrayList<>();
                int max = Math.min(10, imgs.size());
                for (int i = 0; i < max; i++) {
                    var ph = new InputMediaPhoto(imgs.get(i));
                    if (i == 0 && !captionHtml.isBlank()) {
                        ph.setCaption(captionHtml);
                        ph.setParseMode(ParseMode.HTML);
                    }
                    media.add(ph);
                }
                try {
                    tg.execute(new SendMediaGroup(String.valueOf(chatId), media));
                } catch (TelegramApiException e) {
                    System.err.println("[album->single] " + e.getMessage());
                    // фоллбэк по одному
                    for (int i = 0; i < media.size(); i++) {
                        try {
                            tg.execute(SendPhoto.builder()
                                    .chatId(chatId)
                                    .photo(new InputFile(imgs.get(i)))
                                    .build());
                        } catch (TelegramApiException ignore) {}
                    }
                }
                if (parts.size() > 1) sendParts(chatId, parts.subList(1, parts.size()));
            }
        } else {
            // без медиа — текстом, с фоллбэком
            sendParts(chatId, parts);
        }
    }

    // надёжная отправка текста с фоллбэком
    private void sendParts(long chatId, List<String> parts) throws TelegramApiException {
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            try {
                tg.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(p)
                        .parseMode(ParseMode.MARKDOWNV2)
                        .disableWebPagePreview(true)
                        .build());
                continue;
            } catch (TelegramApiRequestException e1) {
                String mono = wrapAsCodeBlock(p);
                try {
                    tg.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(mono)
                            .parseMode(ParseMode.MARKDOWNV2)
                            .disableWebPagePreview(true)
                            .build());
                    continue;
                } catch (TelegramApiException e2) {
                    tg.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(stripMarkdownV2(p))
                            .disableWebPagePreview(true)
                            .build());
                }
            }
        }
    }

    // ---- утилиты ----

    private void sendText(long chatId, String text, ReplyKeyboardMarkup kb) throws TelegramApiException {
        tg.execute(SendMessage.builder()
                .chatId(chatId)
                .text(Markdown.escapeV2(text))
                .parseMode(ParseMode.MARKDOWNV2)
                .replyMarkup(kb)
                .build());
    }

    private ReplyKeyboardMarkup replyKb() {
        KeyboardRow r1 = new KeyboardRow(); r1.add(new KeyboardButton("📰 Лента"));
        KeyboardRow r2 = new KeyboardRow(); r2.add(new KeyboardButton("◀️ Назад")); r2.add(new KeyboardButton("▶️ Вперёд"));
        return ReplyKeyboardMarkup.builder().resizeKeyboard(true).keyboardRow(r1).keyboardRow(r2).build();
    }

    private void safeAnswerOk(String callbackId, String text) {
        try {
            var req = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .text((text == null || text.isBlank()) ? null : (text.length() > 200 ? text.substring(0, 200) : text))
                    .cacheTime(1)
                    .build();
            tg.execute(req);
        } catch (Exception e) {
            System.err.println("[answerOk] " + e.getMessage());
        }
    }

    private void safeAnswerAlert(String callbackId, String text) {
        try {
            var req = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .text(text == null ? "" : (text.length() > 200 ? text.substring(0, 200) : text))
                    .showAlert(true)
                    .cacheTime(0)
                    .build();
            tg.execute(req);
        } catch (Exception e) {
            System.err.println("[answerAlert] " + e.getMessage());
        }
    }

    private void safeSendPlainLink(Long chatId, String url) throws TelegramApiException {
        var txt = Markdown.escapeV2("Не удалось отправить статью. Открой по ссылке: ")
                + "[" + Markdown.escapeV2(url) + "](" + Markdown.escapeUrl(url) + ")";
        tg.execute(SendMessage.builder()
                .chatId(chatId)
                .text(txt)
                .parseMode(ParseMode.MARKDOWNV2)
                .disableWebPagePreview(false)
                .build());
    }

    // --- HTML utils для caption (безопасны для ParseMode.HTML) ---
    private static String htmlEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder((int)(s.length()*1.1));
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default  -> sb.append(ch);
            }
        }
        return sb.toString();
    }
    private static String trimHtmlCaption(String html, int max) {
        if (html.length() <= max) return html;
        String cut = html.substring(0, Math.max(0, max));
        // не оставляем незавершённую HTML-сущность (&... без ;)
        int amp = cut.lastIndexOf('&');
        if (amp != -1) {
            int semi = cut.indexOf(';', amp);
            if (semi == -1) cut = cut.substring(0, amp);
        }
        if (!cut.isEmpty() && cut.charAt(cut.length()-1) == '&') {
            cut = cut.substring(0, cut.length()-1);
        }
        return cut + "…";
    }
    private static String buildCaptionHtml(String title, String summary) {
        String head = "<b>" + htmlEscape(title == null ? "" : title) + "</b>";
        String tail = (summary == null || summary.isBlank()) ? "" : ("\n" + htmlEscape(summary));
        return trimHtmlCaption(head + tail, TG_CAPTION_SAFE);
    }

    // --- разбиение MarkdownV2 на части: отделяем ```блоки``` от plain ---
    private static List<String> splitMarkdownV2Safely(String md) {
        List<String> out = new ArrayList<>();
        Pattern fence = Pattern.compile("(?s)```.*?```");
        Matcher m = fence.matcher(md);
        int last = 0;
        while (m.find()) {
            splitPlain(md.substring(last, m.start()), out);
            splitCodeBlock(md.substring(m.start(), m.end()), out);
            last = m.end();
        }
        splitPlain(md.substring(last), out);
        return out.stream().filter(s -> !s.isBlank()).toList();
    }
    private static void splitPlain(String s, List<String> out) {
        int i = 0;
        while (i < s.length()) {
            int end = Math.min(s.length(), i + TG_MSG_LIMIT - 2);
            String chunk = s.substring(i, end);
            if (chunk.endsWith("\\")) { end--; if (end <= i) break; chunk = s.substring(i, end); }
            out.add(chunk);
            i = end;
        }
    }
    private static void splitCodeBlock(String codeBlock, List<String> out) {
        String lang = "";
        int nl = codeBlock.indexOf('\n', 3);
        if (nl > 0) lang = codeBlock.substring(3, nl).trim();
        String body = codeBlock.replaceFirst("^```[^\\n]*\\n?", "").replaceFirst("```\\s*$", "");
        int overhead = 6 + (lang.isEmpty() ? 0 : lang.length());
        int max = Math.max(100, TG_MSG_LIMIT - overhead);
        for (int i = 0; i < body.length();) {
            int end = Math.min(body.length(), i + max);
            String piece = body.substring(i, end);
            if (piece.endsWith("\\")) { end--; if (end < i) break; piece = body.substring(i, end); }
            out.add("```" + (lang.isEmpty() ? "" : lang) + "\n" + piece + "```");
            i = end;
        }
    }
    private static String wrapAsCodeBlock(String p) {
        String safe = p.replace("```", "` ` `");
        if (!safe.endsWith("\n")) safe += "\n";
        return "```\n" + safe + "```";
    }
    private static String stripMarkdownV2(String p) {
        // упрощённая «очистка» — убираем backslash и спецсимволы MarkdownV2
        return p.replace("\\", "").replaceAll("[*_`~>|=\\[\\](){}#+\\-!.]", " ");
    }

    private String putUrlAndGetToken(String url) {
        String token = new BigInteger(96, rnd).toString(36);
        cbMap.put(token, url);
        return "f:" + token;
    }

    private static final class FeedState { int page = 0; }
}
