package uz.pochtajp.bot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.pochtajp.config.BotProperties;

/**
 * Webhook — prod rejimi (§12, §13).
 *
 * <p>Ikki qatlamli tekshiruv (§7.2):
 * <ol>
 *   <li>{@code X-Telegram-Bot-Api-Secret-Token} headeri — Telegram'ning rasmiy
 *       mexanizmi, asosiy tekshiruv</li>
 *   <li>URL'dagi segment — §12 talab qilgan qo'shimcha to'siq; tasodifiy
 *       skanerlar hatto handler'gacha yetib kelmaydi</li>
 * </ol>
 * Ikkisi ham {@code WEBHOOK_SECRET_TOKEN} bilan solishtiriladi.
 *
 * <p>Javob doim {@code 200} yoki {@code 401} — Telegram xato statusni qayta
 * urinish sifatida tushunadi, shuning uchun update ishlanmasa ham 200 qaytadi
 * va xato faqat log'da qoladi.
 */
@RestController
@ConditionalOnProperty(name = "bot.mode", havingValue = "webhook")
public class BotWebhookController {

    private static final Logger log = LoggerFactory.getLogger(BotWebhookController.class);

    private final BotUpdateHandler handler;
    private final byte[] expectedSecret;

    public BotWebhookController(BotUpdateHandler handler, BotProperties botProperties) {
        this.handler = handler;
        String secret = botProperties.webhookSecretToken();
        this.expectedSecret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/webhook/telegram/{secret}")
    public ResponseEntity<Void> receive(
            @PathVariable String secret,
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String headerSecret,
            @RequestBody Update update) {

        if (expectedSecret.length == 0) {
            log.error("WEBHOOK_SECRET_TOKEN sozlanmagan — webhook qabul qilinmaydi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!matches(secret) || !matches(headerSecret)) {
            log.warn("Webhook siri mos kelmadi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        handler.handle(update);
        return ResponseEntity.ok().build();
    }

    private boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }
}
