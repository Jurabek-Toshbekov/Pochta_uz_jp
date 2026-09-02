package uz.pochtajp.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.pochtajp.bot.BotMessenger;

/**
 * Bot javoblarini eslab qoladi — testlar Telegram API'ga chiqmaydi (§14).
 */
public class StubBotMessenger implements BotMessenger {

    public record SentMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {

        /** Klaviaturadagi barcha tugma matnlari. */
        public List<String> buttonLabels() {
            if (keyboard == null) {
                return List.of();
            }
            return keyboard.getKeyboard().stream()
                    .flatMap(List::stream)
                    .map(InlineKeyboardButton::getText)
                    .toList();
        }

        /** WebApp tugmalari ochadigan manzillar. */
        public List<String> webAppUrls() {
            if (keyboard == null) {
                return List.of();
            }
            return keyboard.getKeyboard().stream()
                    .flatMap(List::stream)
                    .filter(button -> button.getWebApp() != null)
                    .map(button -> button.getWebApp().getUrl())
                    .toList();
        }

        /** URL tugmalari (xabarnomadagi `t.me` havolalari). */
        public List<String> buttonUrls() {
            if (keyboard == null) {
                return List.of();
            }
            return keyboard.getKeyboard().stream()
                    .flatMap(List::stream)
                    .map(InlineKeyboardButton::getUrl)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        public List<String> callbackData() {
            if (keyboard == null) {
                return List.of();
            }
            return keyboard.getKeyboard().stream()
                    .flatMap(List::stream)
                    .map(InlineKeyboardButton::getCallbackData)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }

    public record SentDocument(long chatId, String fileName, byte[] content, String caption) {
    }

    private final List<SentMessage> messages = new CopyOnWriteArrayList<>();
    private final List<SentDocument> documents = new CopyOnWriteArrayList<>();
    private final List<String> answeredCallbacks = new CopyOnWriteArrayList<>();
    private volatile int commandMenuPublishes = 0;
    private volatile boolean failNextSend = false;

    @Override
    public boolean sendHtml(long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (failNextSend) {
            return false;
        }
        messages.add(new SentMessage(chatId, text, keyboard));
        return true;
    }

    /** Odam botni bloklagan holatni sinash uchun. */
    public void failSends() {
        failNextSend = true;
    }

    @Override
    public void answerCallback(String callbackQueryId, String text) {
        answeredCallbacks.add(callbackQueryId);
    }

    @Override
    public void sendDocument(long chatId, String fileName, byte[] content, String caption) {
        documents.add(new SentDocument(chatId, fileName, content, caption));
    }

    @Override
    public void publishCommandMenu() {
        commandMenuPublishes++;
    }

    public List<SentMessage> messages() {
        return List.copyOf(messages);
    }

    public SentMessage lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public List<SentDocument> documents() {
        return List.copyOf(documents);
    }

    public List<String> answeredCallbacks() {
        return List.copyOf(answeredCallbacks);
    }

    public int commandMenuPublishes() {
        return commandMenuPublishes;
    }

    public void reset() {
        messages.clear();
        documents.clear();
        answeredCallbacks.clear();
        commandMenuPublishes = 0;
        failNextSend = false;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public StubBotMessenger stubBotMessenger() {
            return new StubBotMessenger();
        }
    }
}
