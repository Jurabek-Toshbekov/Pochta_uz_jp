package uz.pochtajp.bot;

import java.util.Arrays;
import java.util.Optional;

/**
 * Bot buyruqlari (§8.1). Bot endi forma to'ldirmaydi — faqat kutib oladi,
 * tushuntiradi, yo'naltiradi va xabar beradi (§8, §15).
 */
public enum BotCommand {

    START("/start", "Boshlash"),
    NEW_POST("/elon", "E'lon berish"),
    SEARCH("/qidiruv", "Qidirish"),
    MY_POSTS("/mening_elonlarim", "Mening e'lonlarim"),
    SUBSCRIPTIONS("/obuna", "Xabarnoma obunalari"),
    SAFETY("/xavfsizlik", "Xavfsizlik qoidalari"),
    RULES("/qoidalar", "Qoidalar"),
    LANGUAGE("/til", "Til"),
    HELP("/yordam", "Yordam"),
    MY_DATA("/mening_malumotlarim", "Mening ma'lumotlarim");

    private final String command;
    private final String description;

    BotCommand(String command, String description) {
        this.command = command;
        this.description = description;
    }

    public String command() {
        return command;
    }

    /** {@code /start} -> {@code start} — Telegram menyusiga qo'yish uchun. */
    public String slug() {
        return command.substring(1);
    }

    public String description() {
        return description;
    }

    /**
     * Matndan buyruqni ajratadi. Guruhda buyruq {@code /elon@bot_username}
     * ko'rinishida keladi, shuni ham qo'llab-quvvatlaydi.
     */
    public static Optional<BotCommand> parse(String text) {
        if (text == null || !text.startsWith("/")) {
            return Optional.empty();
        }
        String head = text.split("\\s+", 2)[0];
        int at = head.indexOf('@');
        String candidate = (at > 0 ? head.substring(0, at) : head).toLowerCase();
        return Arrays.stream(values())
                .filter(value -> value.command.equals(candidate))
                .findFirst();
    }

    /** {@code /start ch_abc} -> {@code ch_abc}. */
    public static Optional<String> payload(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parts[1].strip());
    }
}
