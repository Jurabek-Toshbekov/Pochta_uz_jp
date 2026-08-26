package uz.pochtajp.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §16, 6-band: UI matnlari uch tilda. Va §2, 8-nuqson: bot g'azablanmaydi.
 */
class BotTextsTest {

    private static final List<String> LANGUAGES = List.of("uz", "uz-cyrl", "ru");

    @Test
    @DisplayName("Uch til ham mavjud")
    void allLanguagesPresent() {
        assertThat(BotTexts.all().keySet()).containsExactlyInAnyOrderElementsOf(LANGUAGES);
    }

    @Test
    @DisplayName("Hech bir matn bo'sh emas")
    void noBlankTexts() throws Exception {
        for (Map.Entry<String, BotTexts.Pack> entry : BotTexts.all().entrySet()) {
            for (String value : values(entry.getValue())) {
                assertThat(value)
                        .as("til: %s", entry.getKey())
                        .isNotNull()
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("/start matnida ism o'rni bor va WebApp tugmasi haqida aytiladi (§8.2)")
    void startMentionsButton() {
        for (String language : LANGUAGES) {
            BotTexts.Pack pack = BotTexts.of(language);
            assertThat(pack.start()).as("til: %s", language).contains("%s");
            assertThat(pack.start().formatted("Ali")).contains("Ali");
            assertThat(pack.start()).contains("/xavfsizlik");
        }
    }

    @Test
    @DisplayName("Hech qanday jerkish yo'q: 😡 va o'xshash emoji ishlatilmaydi (§2, 8-nuqson)")
    void noAngryEmoji() throws Exception {
        List<String> forbidden = List.of("😡", "😠", "🤬", "👎");
        for (Map.Entry<String, BotTexts.Pack> entry : BotTexts.all().entrySet()) {
            for (String value : values(entry.getValue())) {
                for (String emoji : forbidden) {
                    assertThat(value).as("til: %s", entry.getKey()).doesNotContain(emoji);
                }
            }
        }
    }

    @Test
    @DisplayName("Xavfsizlik matnida taqiqlangan buyumlar sanab o'tiladi (§7.3)")
    void safetyListsProhibitedItems() {
        assertThat(BotTexts.of("uz").safety())
                .contains("giyohvand")
                .contains("go'sht")
                .contains("retseptli");
        assertThat(BotTexts.of("ru").safety())
                .contains("наркотические")
                .contains("рецептурные");
        assertThat(BotTexts.of("uz-cyrl").safety()).contains("гиёҳванд");
    }

    @Test
    @DisplayName("Erkin matnga javob muloyim va nima qilishni aytadi (§8.3)")
    void freeTextIsPolite() {
        for (String language : LANGUAGES) {
            String text = BotTexts.of(language).freeText();
            assertThat(text).as("til: %s", language).isNotBlank();
            assertThat(text.length()).isLessThan(200);
        }
    }

    @Test
    @DisplayName("Noma'lum til so'ralsa uz qaytadi")
    void fallsBackToUz() {
        assertThat(BotTexts.of("en")).isSameAs(BotTexts.of("uz"));
        assertThat(BotTexts.of(null)).isSameAs(BotTexts.of("uz"));
    }

    private List<String> values(BotTexts.Pack pack) throws Exception {
        List<String> result = new ArrayList<>();
        for (RecordComponent component : BotTexts.Pack.class.getRecordComponents()) {
            result.add((String) component.getAccessor().invoke(pack));
        }
        return result;
    }
}
