package uz.pochtajp.support;

import java.util.List;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/** Testlar uchun Telegram {@code Update} yasovchi. */
public final class BotUpdates {

    public static final long TELEGRAM_ID = 909_000_111L;
    public static final long CHAT_ID = 909_000_111L;

    private BotUpdates() {
    }

    public static User user(long telegramId, String languageCode) {
        User user = new User(telegramId, "Test", false);
        user.setLastName("Foydalanuvchi");
        user.setUserName("testuser");
        user.setLanguageCode(languageCode);
        return user;
    }

    private static Chat privateChat(long chatId) {
        return new Chat(chatId, "private");
    }

    private static Message baseMessage(long telegramId, String languageCode) {
        Message message = new Message();
        message.setMessageId(1);
        message.setDate((int) (System.currentTimeMillis() / 1000));
        message.setChat(privateChat(CHAT_ID));
        message.setFrom(user(telegramId, languageCode));
        return message;
    }

    public static Update text(String text) {
        return text(text, TELEGRAM_ID, "uz");
    }

    public static Update text(String text, long telegramId, String languageCode) {
        Message message = baseMessage(telegramId, languageCode);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    /** Rasm yuborilgan holat — bot muloyim javob berishi kerak (§8.3). */
    public static Update photo() {
        Message message = baseMessage(TELEGRAM_ID, "uz");
        PhotoSize size = new PhotoSize();
        size.setFileId("file-1");
        size.setFileUniqueId("uniq-1");
        size.setWidth(100);
        size.setHeight(100);
        message.setPhoto(List.of(size));
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    public static Update callback(String data) {
        return callback(data, TELEGRAM_ID);
    }

    public static Update callback(String data, long telegramId) {
        Message message = baseMessage(telegramId, "uz");
        message.setText("menu");

        CallbackQuery query = new CallbackQuery();
        query.setId("cb-1");
        query.setFrom(user(telegramId, "uz"));
        query.setMessage(message);
        query.setData(data);
        query.setChatInstance("chat-instance");

        Update update = new Update();
        update.setCallbackQuery(query);
        return update;
    }

    /** Guruh xabari — bot javob bermasligi kerak. */
    public static Update groupText(String text) {
        Message message = new Message();
        message.setMessageId(2);
        message.setDate((int) (System.currentTimeMillis() / 1000));
        message.setChat(new Chat(-1001L, "supergroup"));
        message.setFrom(user(TELEGRAM_ID, "uz"));
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
