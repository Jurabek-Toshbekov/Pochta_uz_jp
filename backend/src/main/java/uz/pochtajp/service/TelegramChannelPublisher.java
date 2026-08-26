package uz.pochtajp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import uz.pochtajp.config.BotProperties;

/**
 * Haqiqiy Telegram kanaliga yuboradi (§8.3 — "Kanal'ga publish qilish").
 *
 * <p>Token log'ga hech qachon chiqmaydi (§1.2). Xato bo'lsa
 * {@link ChannelPublishException} tashlanadi va {@link PublishService} e'lonni
 * {@code PENDING} holatida qoldiradi — ma'lumot yo'qolmaydi.
 */
@Component
public class TelegramChannelPublisher implements ChannelPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelegramChannelPublisher.class);

    private final TelegramClient telegramClient;
    private final String channelChatId;

    public TelegramChannelPublisher(BotProperties botProperties) {
        this.telegramClient = new OkHttpTelegramClient(botProperties.token());
        this.channelChatId = botProperties.channelChatId();
    }

    @Override
    public boolean isConfigured() {
        return channelChatId != null && !channelChatId.isBlank();
    }

    @Override
    public long publishToChannel(String html) {
        if (!isConfigured()) {
            throw new ChannelPublishException("CHANNEL_NOT_CONFIGURED",
                    "CHANNEL_CHAT_ID sozlanmagan — e'lon kanalga yuborilmadi");
        }
        SendMessage message = SendMessage.builder()
                .chatId(channelChatId)
                .text(html)
                .parseMode("HTML")
                // Deep link uchun havola oldindan ko'rinishi kerak emas — post uzayib ketadi.
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .build();
        try {
            Message sent = telegramClient.execute(message);
            log.info("Kanalga yuborildi: message_id={}", sent.getMessageId());
            return sent.getMessageId();
        } catch (TelegramApiException ex) {
            throw new ChannelPublishException("TELEGRAM_API_ERROR",
                    "Telegram API xatosi: " + ex.getMessage(), ex);
        }
    }
}
