package kinet.smaug;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Bot {
    public static void main(String[] args) throws Exception {
        String token = System.getenv("token");
        var app = new TelegramBotsLongPollingApplication();
        app.registerBot(token, new HabrBot(new OkHttpTelegramClient(token)));
        System.out.println("KWD bot started.");
    }
}
