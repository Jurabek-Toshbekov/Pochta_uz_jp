package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.common.exception.ValidationException;

/** Keyset kursori (§10.2). */
class SearchCursorTest {

    private static final UUID ID = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Test
    @DisplayName("Kodlanadi va qaytadan o'qiladi")
    void roundTrip() {
        SearchCursor cursor = new SearchCursor("2026-08-26T10:00:00Z", ID);

        SearchCursor decoded = SearchCursor.decode(cursor.encode()).orElseThrow();

        assertThat(decoded.key()).isEqualTo("2026-08-26T10:00:00Z");
        assertThat(decoded.id()).isEqualTo(ID);
    }

    @Test
    @DisplayName("Kalitda ajratuvchi belgi bo'lsa ham buzilmaydi")
    void handlesSeparatorInsideKey() {
        SearchCursor cursor = new SearchCursor("a|b|c", ID);

        SearchCursor decoded = SearchCursor.decode(cursor.encode()).orElseThrow();

        assertThat(decoded.key()).isEqualTo("a|b|c");
        assertThat(decoded.id()).isEqualTo(ID);
    }

    @Test
    @DisplayName("URL'da xavfsiz belgilar ishlatiladi")
    void encodesUrlSafe() {
        String encoded = new SearchCursor("2026-08-26T10:00:00Z", ID).encode();

        assertThat(encoded).doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("Bo'sh kursor — birinchi sahifa")
    void emptyMeansFirstPage() {
        assertThat(SearchCursor.decode(null)).isEmpty();
        assertThat(SearchCursor.decode("")).isEmpty();
        assertThat(SearchCursor.decode("   ")).isEmpty();
    }

    @Test
    @DisplayName("Buzilgan kursor — tushunarli xato, 500 emas")
    void rejectsBrokenCursor() {
        assertThatThrownBy(() -> SearchCursor.decode("!!!not-base64!!!"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Sahifa belgisi");

        assertThatThrownBy(() -> SearchCursor.decode(
                java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("no-separator".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(ValidationException.class);
    }
}
