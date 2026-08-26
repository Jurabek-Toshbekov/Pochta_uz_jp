package uz.pochtajp.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Botning tashqi dunyoga chiqishi. Interfeys ataylab: testlarda haqiqiy
 * Telegram API'ga chiqmasdan butun dialog oqimini tekshirish uchun (§14).
 */
public interface BotMessenger {

    /** HTML {@code parse_mode} bilan xabar. Keyboard {@code null} bo'lishi mumkin. */
    void sendHtml(long chatId, String text, InlineKeyboardMarkup keyboard);

    /** Inline tugma bosilganini tasdiqlash — aks holda tugmada "soat" aylanib turadi. */
    void answerCallback(String callbackQueryId, String text);

    /** Ma'lumot eksporti JSON fayl sifatida yuboriladi (§7.2). */
    void sendDocument(long chatId, String fileName, byte[] content, String caption);

    /** Telegram menyusidagi buyruqlar ro'yxati. */
    void publishCommandMenu();
}
