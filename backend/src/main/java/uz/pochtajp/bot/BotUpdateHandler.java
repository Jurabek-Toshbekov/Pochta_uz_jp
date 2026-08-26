package uz.pochtajp.bot;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.common.TelegramHtml;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.config.BotProperties;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.service.UserDataService;
import uz.pochtajp.service.UserService;

/**
 * Botning yuragi (§8).
 *
 * <p>Bot <b>forma to'ldirmaydi</b> — {@code stepNumber} mantiqi butunlay yo'q (§15).
 * Uning ishi: kutib olish, tushuntirish, Mini App'ga yo'naltirish, xabar berish.
 *
 * <p>Har bir buyruq {@code bot_command} eventi sifatida yoziladi (§6.1).
 */
@Component
public class BotUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(BotUpdateHandler.class);

    private final UserService userService;
    private final UserDataService userDataService;
    private final BotMessenger messenger;
    private final BotKeyboards keyboards;
    private final BotProperties botProperties;
    private final EventLogger eventLogger;

    public BotUpdateHandler(UserService userService,
                            UserDataService userDataService,
                            BotMessenger messenger,
                            BotKeyboards keyboards,
                            BotProperties botProperties,
                            EventLogger eventLogger) {
        this.userService = userService;
        this.userDataService = userDataService;
        this.messenger = messenger;
        this.keyboards = keyboards;
        this.botProperties = botProperties;
        this.eventLogger = eventLogger;
    }

    /**
     * Bitta update. Bu metod hech qachon exception tashlamaydi: bitta buzilgan
     * update butun polling oqimini to'xtatmasligi kerak.
     */
    public void handle(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (ForbiddenException ex) {
            // Bloklangan foydalanuvchi — sababini aytamiz va to'xtaymiz.
            replyBlocked(update);
        } catch (Exception ex) {
            log.error("Update ishlanmadi", ex);
            replyError(update);
        }
    }

    // ------------------------------------------------------------------
    // Xabarlar
    // ------------------------------------------------------------------

    private void handleMessage(Message message) {
        User from = message.getFrom();
        if (from == null || Boolean.TRUE.equals(from.getIsBot())) {
            return;
        }
        // Guruh/kanal xabarlariga javob bermaydi — bot faqat shaxsiy chatda ishlaydi.
        if (!message.isUserMessage()) {
            return;
        }

        UserService.Session session = userService.upsert(toProfile(from), "bot");
        uz.pochtajp.domain.User user = session.user();
        BotTexts.Pack texts = BotTexts.of(user.getUiLanguage());
        long chatId = message.getChatId();

        if (hasMedia(message)) {
            messenger.sendHtml(chatId, texts.media(), keyboards.mainMenu(texts));
            return;
        }

        String text = message.hasText() ? message.getText() : null;
        Optional<BotCommand> command = BotCommand.parse(text);

        if (command.isEmpty()) {
            // Erkin matn — muloyim javob va tugma. Hech qanday jerkish (§8.3).
            messenger.sendHtml(chatId,
                    text != null && text.startsWith("/") ? texts.unknownCommand() : texts.freeText(),
                    keyboards.mainMenu(texts));
            return;
        }

        BotCommand resolved = command.get();
        eventLogger.track(TrackedEvent.of(EventName.BOT_COMMAND, EventSource.BOT)
                .user(user.getId())
                .property("command", resolved.command())
                .build());

        switch (resolved) {
            case START -> handleStart(chatId, user, texts, BotCommand.payload(text).orElse(null));
            case NEW_POST -> sendWithRoute(chatId, texts.newPostPrompt(), texts.btnNewPost(), "/new", texts);
            case SEARCH -> sendWithRoute(chatId, texts.searchPrompt(), texts.btnSearch(), "/search", texts);
            case MY_POSTS -> sendWithRoute(chatId, texts.myPostsPrompt(), texts.btnMyPosts(), "/my", texts);
            case SUBSCRIPTIONS -> messenger.sendHtml(chatId, texts.subscriptionsSoon(), null);
            case SAFETY -> messenger.sendHtml(chatId, texts.safety(), null);
            case RULES -> messenger.sendHtml(chatId, texts.rules(), null);
            case LANGUAGE -> messenger.sendHtml(chatId, texts.languageTitle(), keyboards.languageChoice());
            case HELP -> messenger.sendHtml(chatId, texts.help(), keyboards.mainMenu(texts));
            case MY_DATA -> messenger.sendHtml(chatId, texts.myData(), keyboards.myDataMenu(texts));
        }
    }

    private void handleStart(long chatId, uz.pochtajp.domain.User user, BotTexts.Pack texts, String payload) {
        String name = user.getFirstName() == null ? "do'stim" : TelegramHtml.escape(user.getFirstName());
        messenger.sendHtml(chatId, texts.start().formatted(name), keyboards.mainMenu(texts));

        if (payload != null) {
            // Deep link atributsiyasi (§6.4, 6-band). `startapp` Mini App'ga
            // to'g'ridan-to'g'ri tushadi; bu yerga `?start=` varianti keladi.
            eventLogger.track(TrackedEvent.of(EventName.DEEP_LINK_OPEN, EventSource.BOT)
                    .user(user.getId())
                    .property("start_param", payload)
                    .build());
        }
    }

    private void sendWithRoute(long chatId, String text, String buttonLabel, String route,
                               BotTexts.Pack texts) {
        if (!botProperties.hasMiniapp()) {
            messenger.sendHtml(chatId, texts.miniappNotConfigured(), null);
            return;
        }
        messenger.sendHtml(chatId, text, keyboards.single(buttonLabel, route));
    }

    // ------------------------------------------------------------------
    // Inline tugmalar
    // ------------------------------------------------------------------

    private void handleCallback(CallbackQuery callback) {
        User from = callback.getFrom();
        if (from == null) {
            return;
        }
        UserService.Session session = userService.upsert(toProfile(from), "bot");
        uz.pochtajp.domain.User user = session.user();
        BotTexts.Pack texts = BotTexts.of(user.getUiLanguage());

        String data = callback.getData() == null ? "" : callback.getData();
        Long chatId = callback.getMessage() == null ? null : callback.getMessage().getChatId();
        if (chatId == null) {
            messenger.answerCallback(callback.getId(), "");
            return;
        }

        if (data.startsWith(BotKeyboards.CB_LANG_PREFIX)) {
            String language = data.substring(BotKeyboards.CB_LANG_PREFIX.length());
            if (!BotTexts.all().containsKey(language)) {
                messenger.answerCallback(callback.getId(), "");
                return;
            }
            String previous = user.getUiLanguage();
            userService.setUiLanguage(user.getId(), language);
            BotTexts.Pack updated = BotTexts.of(language);
            eventLogger.track(TrackedEvent.of(EventName.LANGUAGE_CHANGED, EventSource.BOT)
                    .user(user.getId())
                    .property("from", previous)
                    .property("to", language)
                    .build());
            messenger.answerCallback(callback.getId(), updated.languageChanged());
            messenger.sendHtml(chatId, updated.languageChanged(), keyboards.mainMenu(updated));
            return;
        }

        switch (data) {
            case BotKeyboards.CB_DATA_EXPORT -> {
                byte[] json = userDataService.exportAsJson(user.getId());
                messenger.answerCallback(callback.getId(), "");
                messenger.sendDocument(chatId, "pochta-malumotlarim.json", json, texts.dataExportCaption());
            }
            case BotKeyboards.CB_DATA_DELETE -> {
                messenger.answerCallback(callback.getId(), "");
                messenger.sendHtml(chatId, texts.dataDeleteConfirm(), keyboards.deleteConfirm(texts));
            }
            case BotKeyboards.CB_DATA_DELETE_CONFIRM -> {
                userDataService.deletePersonalData(user.getId());
                messenger.answerCallback(callback.getId(), "");
                messenger.sendHtml(chatId, texts.dataDeleted(), null);
            }
            case BotKeyboards.CB_DATA_CANCEL -> {
                messenger.answerCallback(callback.getId(), "");
                messenger.sendHtml(chatId, texts.dataDeleteCancelled(), null);
            }
            default -> messenger.answerCallback(callback.getId(), "");
        }
    }

    // ------------------------------------------------------------------

    private UserService.TelegramProfile toProfile(User from) {
        return new UserService.TelegramProfile(
                from.getId(),
                from.getUserName(),
                from.getFirstName(),
                from.getLastName(),
                from.getLanguageCode(),
                Boolean.TRUE.equals(from.getIsPremium()));
    }

    private boolean hasMedia(Message message) {
        return message.hasPhoto() || message.hasDocument() || message.hasVideo()
                || message.hasAudio() || message.hasVoice() || message.hasSticker()
                || message.hasAnimation() || message.hasVideoNote();
    }

    private void replyBlocked(Update update) {
        Long chatId = chatIdOf(update);
        if (chatId != null) {
            messenger.sendHtml(chatId, BotTexts.of("uz").blocked(), null);
        }
    }

    private void replyError(Update update) {
        Long chatId = chatIdOf(update);
        if (chatId != null) {
            messenger.sendHtml(chatId, BotTexts.of("uz").genericError(), null);
        }
    }

    private Long chatIdOf(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }
}
