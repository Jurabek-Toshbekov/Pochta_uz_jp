package uz.pochtajp.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.pochtajp.config.BotMode;
import uz.pochtajp.config.BotProperties;

/**
 * Webhook siri tekshiruvi (§7.2). Bu — botga tashqi dunyodan kiradigan
 * yagona eshik, shuning uchun har bir rad etish holati alohida tekshiriladi.
 */
class BotWebhookControllerTest {

    private static final String SECRET = "s3cr3t-webhook-token";

    private final BotUpdateHandler handler = mock(BotUpdateHandler.class);

    private BotWebhookController controllerWithSecret(String secret) {
        BotProperties properties = new BotProperties(
                "1:token", "test_bot", null, null, null, 86_400L, BotMode.WEBHOOK, null, secret);
        return new BotWebhookController(handler, properties);
    }

    @Test
    @DisplayName("To'g'ri sir: yo'lda ham, headerda ham — update ishlanadi")
    void acceptsMatchingSecret() {
        ResponseEntity<Void> response =
                controllerWithSecret(SECRET).receive(SECRET, SECRET, new Update());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(handler, times(1)).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Header yo'q — 401, update ishlanmaydi")
    void rejectsMissingHeader() {
        ResponseEntity<Void> response =
                controllerWithSecret(SECRET).receive(SECRET, null, new Update());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Header boshqa qiymat — 401")
    void rejectsWrongHeader() {
        ResponseEntity<Void> response =
                controllerWithSecret(SECRET).receive(SECRET, "boshqa", new Update());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Yo'ldagi segment boshqa — 401")
    void rejectsWrongPathSegment() {
        ResponseEntity<Void> response =
                controllerWithSecret(SECRET).receive("boshqa", SECRET, new Update());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Sir sozlanmagan — hech kim qabul qilinmaydi")
    void rejectsWhenSecretNotConfigured() {
        ResponseEntity<Void> response =
                controllerWithSecret("").receive("", "", new Update());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }
}
