package uz.pochtajp.bot;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * {@link BotMessenger}ning haqiqiy implementatsiyasi.
 *
 * <p>Xato bo'lsa faqat log'ga yoziladi: bitta foydalanuvchiga xabar
 * yetmagani boshqa update'larni to'xtatmasligi kerak. Log'da chat_id bor,
 * xabar matni yo'q — ichida PII bo'lishi mumkin (§1.7).
 */
@Component
public class TelegramBotMessenger implements BotMessenger {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotMessenger.class);

    private final TelegramClient telegramClient;

    public TelegramBotMessenger(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    @Override
    public boolean sendHtml(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(message);
            return true;
        } catch (TelegramApiException ex) {
            log.warn("Xabar yuborilmadi: chat_id={} sabab={}", chatId, ex.getMessage());
            return false;
        }
    }

    @Override
    public void answerCallback(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .build();
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException ex) {
            log.warn("Callback javobi yuborilmadi: sabab={}", ex.getMessage());
        }
    }

    @Override
    public void sendDocument(long chatId, String fileName, byte[] content, String caption) {
        SendDocument document = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new ByteArrayInputStream(content), fileName))
                .caption(caption)
                .build();
        try {
            telegramClient.execute(document);
        } catch (TelegramApiException ex) {
            log.warn("Fayl yuborilmadi: chat_id={} sabab={}", chatId, ex.getMessage());
        }
    }

    @Override
    public void publishCommandMenu() {
        var commands = Arrays.stream(BotCommand.values())
                .filter(BotCommand::inMenu)
                .map(command -> new org.telegram.telegrambots.meta.api.objects.commands.BotCommand(
                        command.slug(), command.description()))
                .toList();
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .commands(commands)
                    .scope(new BotCommandScopeDefault())
                    .build());
            log.info("Buyruqlar menyusi yangilandi: {} ta", commands.size());
        } catch (TelegramApiException ex) {
            log.warn("Buyruqlar menyusini yangilab bo'lmadi: {}", ex.getMessage());
        }
    }
}
