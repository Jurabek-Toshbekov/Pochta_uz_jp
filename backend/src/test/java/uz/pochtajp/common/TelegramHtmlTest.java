package uz.pochtajp.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** §7.2 — kanalga chiqishdan oldin {@code < > &} escape qilinadi. */
class TelegramHtmlTest {

    @Test
    @DisplayName("Uchta belgi escape qilinadi")
    void escapesThreeCharacters() {
        assertThat(TelegramHtml.escape("a < b > c & d"))
                .isEqualTo("a &lt; b &gt; c &amp; d");
    }

    @Test
    @DisplayName("Teg va havola matn sifatida qoladi")
    void neutralizesTags() {
        String result = TelegramHtml.escape("<a href=\"http://scam\">bosing</a>");

        assertThat(result)
                .doesNotContain("<a")
                .contains("&lt;a href=")
                .contains("bosing");
    }

    @Test
    @DisplayName("null va bo'sh matn — bo'sh natija")
    void handlesNull() {
        assertThat(TelegramHtml.escape(null)).isEmpty();
        assertThat(TelegramHtml.escape("")).isEmpty();
    }

    @Test
    @DisplayName("O'zbek harflari o'zgarmaydi")
    void keepsUzbekLetters() {
        assertThat(TelegramHtml.escape("O'zbekiston, Farg'ona")).isEqualTo("O'zbekiston, Farg'ona");
    }

    @Test
    @DisplayName("Hashtag tokeni — faqat harf, raqam, pastki chiziq")
    void sanitizesHashtagToken() {
        assertThat(TelegramHtml.hashtagToken("JP_UZ")).isEqualTo("JP_UZ");
        assertThat(TelegramHtml.hashtagToken("a b-c!#")).isEqualTo("abc");
        assertThat(TelegramHtml.hashtagToken(null)).isEmpty();
    }
}
