package uz.pochtajp.bot;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import uz.pochtajp.config.BotProperties;

/**
 * WebApp tugmalari (§8.2). Bot foydalanuvchini Mini App'ga yo'naltiradi.
 *
 * <p>Mini App HashRouter ishlatadi, shuning uchun tugma to'g'ridan-to'g'ri
 * kerakli ekranni ochadi: {@code https://app.example.com/#/new}.
 */
@Component
public class BotKeyboards {

    /** Callback data prefikslari — 64 baytdan oshmaydi (Telegram cheklovi). */
    public static final String CB_LANG_PREFIX = "lang:";
    public static final String CB_DATA_EXPORT = "data:export";
    public static final String CB_DATA_DELETE = "data:delete";
    public static final String CB_DATA_DELETE_CONFIRM = "data:delete:yes";
    public static final String CB_DATA_CANCEL = "data:cancel";

    private final BotProperties botProperties;

    public BotKeyboards(BotProperties botProperties) {
        this.botProperties = botProperties;
    }

    /** {@code /start} klaviaturasi: e'lon berish + qidiruv/e'lonlarim. */
    public InlineKeyboardMarkup mainMenu(BotTexts.Pack texts) {
        if (!botProperties.hasMiniapp()) {
            return null;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(webApp(texts.btnNewPost(), "/new")));
        rows.add(new InlineKeyboardRow(
                webApp(texts.btnSearch(), "/search"),
                webApp(texts.btnMyPosts(), "/my")));
        return new InlineKeyboardMarkup(rows);
    }

    /** Bitta ekranni ochadigan yakka tugma. */
    public InlineKeyboardMarkup single(String label, String route) {
        if (!botProperties.hasMiniapp()) {
            return null;
        }
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(webApp(label, route))));
    }

    public InlineKeyboardMarkup languageChoice() {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(callback("O'zbekcha", CB_LANG_PREFIX + "uz")));
        rows.add(new InlineKeyboardRow(callback("Ўзбекча", CB_LANG_PREFIX + "uz-cyrl")));
        rows.add(new InlineKeyboardRow(callback("Русский", CB_LANG_PREFIX + "ru")));
        return new InlineKeyboardMarkup(rows);
    }

    public InlineKeyboardMarkup myDataMenu(BotTexts.Pack texts) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(callback(texts.btnExportData(), CB_DATA_EXPORT)));
        rows.add(new InlineKeyboardRow(callback(texts.btnDeleteData(), CB_DATA_DELETE)));
        return new InlineKeyboardMarkup(rows);
    }

    public InlineKeyboardMarkup deleteConfirm(BotTexts.Pack texts) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(callback(texts.btnDeleteData(), CB_DATA_DELETE_CONFIRM)));
        rows.add(new InlineKeyboardRow(callback(texts.btnCancel(), CB_DATA_CANCEL)));
        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardButton webApp(String label, String route) {
        InlineKeyboardButton button = new InlineKeyboardButton(label);
        button.setWebApp(new WebAppInfo(botProperties.miniappUrlFor(route)));
        return button;
    }

    private InlineKeyboardButton callback(String label, String data) {
        InlineKeyboardButton button = new InlineKeyboardButton(label);
        button.setCallbackData(data);
        return button;
    }
}
