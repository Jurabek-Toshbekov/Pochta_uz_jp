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

    /** "Odam topdingizmi?" javoblari (§6.4, 1-band). */
    public static final String CB_DEAL_FOUND = "deal:found:";
    public static final String CB_DEAL_NOT_YET = "deal:wait:";
    public static final String CB_DEAL_CANCELLED = "deal:cancel:";

    /** Baho: {@code review:<postId>:<1..5>} (§6.4, 7-band). */
    public static final String CB_REVIEW_PREFIX = "review:";

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

    /**
     * "Odam topdingizmi?" so'rovi (§6.4, 1-band).
     *
     * <p>Uchta javob ataylab: "javob bo'lmadi" varianti mahsulot muammosini
     * ko'rsatadi, "rejam o'zgardi" esa oddiy hayot. Ikkisini bir joyga
     * qo'shib yuborish ma'lumotni yo'qotadi (§6.4, 3-band).
     */
    public InlineKeyboardMarkup dealAsk(BotTexts.Pack texts, java.util.UUID postId) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(callback(texts.btnDealFound(), CB_DEAL_FOUND + postId)));
        rows.add(new InlineKeyboardRow(callback(texts.btnDealNotYet(), CB_DEAL_NOT_YET + postId)));
        rows.add(new InlineKeyboardRow(callback(texts.btnDealCancelled(), CB_DEAL_CANCELLED + postId)));
        return new InlineKeyboardMarkup(rows);
    }

    /** 1..5 yulduz — bitta qatorda. */
    public InlineKeyboardMarkup reviewStars(BotTexts.Pack texts, java.util.UUID postId) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        for (int stars = 1; stars <= 5; stars++) {
            row.add(callback(texts.btnReviewStar().formatted(stars),
                    CB_REVIEW_PREFIX + postId + ":" + stars));
        }
        return new InlineKeyboardMarkup(List.of(row));
    }

    /**
     * Xabarnomadagi havola: aynan shu e'lon ochiladi va ochilish yoziladi.
     *
     * <p>WebApp tugmasi emas, URL tugmasi — {@code startapp} parametri
     * faqat {@code t.me} havolasi orqali Mini App'ga yetib boradi (§10.3).
     */
    public InlineKeyboardMarkup openPost(String label, java.util.UUID postId) {
        InlineKeyboardButton button = new InlineKeyboardButton(label);
        button.setUrl(botProperties.notificationLinkForPost(postId));
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
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
