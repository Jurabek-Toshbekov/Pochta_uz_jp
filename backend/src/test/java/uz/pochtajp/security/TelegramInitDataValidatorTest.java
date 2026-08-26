package uz.pochtajp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.common.exception.InitDataInvalidException;
import uz.pochtajp.config.BotMode;
import uz.pochtajp.config.BotProperties;
import uz.pochtajp.support.InitDataFactory;

/**
 * §14 talabi: to'g'ri imzo, buzilgan imzo, eskirgan {@code auth_date} —
 * uchtasi ham alohida tekshiriladi.
 */
class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "1234567890:TEST-ONLY-abcdefghijklmnop";
    private static final long TELEGRAM_ID = 555_000_111L;

    private final TelegramInitDataValidator validator = new TelegramInitDataValidator(
            new BotProperties(BOT_TOKEN, "test_bot", null, null, null, 86_400L, BotMode.OFF, null, null),
            new ObjectMapper());

    @Test
    @DisplayName("To'g'ri imzo — foydalanuvchi qaytadi")
    void acceptsValidSignature() {
        String initData = InitDataFactory.valid(BOT_TOKEN, TELEGRAM_ID);

        TelegramInitData result = validator.validate(initData);

        assertThat(result.user().id()).isEqualTo(TELEGRAM_ID);
        assertThat(result.user().username()).isEqualTo("testuser");
        assertThat(result.user().languageCode()).isEqualTo("uz");
        assertThat(result.authDate()).isNotNull();
    }

    @Test
    @DisplayName("start_param o'qiladi — deep link atributsiyasi uchun")
    void readsStartParam() {
        String initData = InitDataFactory.validWithStartParam(BOT_TOKEN, TELEGRAM_ID, "ch_abc123");

        assertThat(validator.validate(initData).startParam()).isEqualTo("ch_abc123");
    }

    @Test
    @DisplayName("Imzodan keyin o'zgartirilgan ma'lumot — 401")
    void rejectsTamperedData() {
        String initData = InitDataFactory.tampered(BOT_TOKEN, TELEGRAM_ID);

        assertThatThrownBy(() -> validator.validate(initData))
                .isInstanceOf(InitDataInvalidException.class)
                .hasMessageContaining("imzosi yaroqsiz");
    }

    @Test
    @DisplayName("Boshqa bot tokeni bilan imzolangan — 401")
    void rejectsForeignToken() {
        String initData = InitDataFactory.valid("9999999999:OTHER-BOT-TOKEN", TELEGRAM_ID);

        assertThatThrownBy(() -> validator.validate(initData))
                .isInstanceOf(InitDataInvalidException.class);
    }

    @Test
    @DisplayName("Eskirgan auth_date — 401")
    void rejectsExpiredAuthDate() {
        String initData = InitDataFactory.expired(BOT_TOKEN, TELEGRAM_ID, 86_400L + 60L);

        assertThatThrownBy(() -> validator.validate(initData))
                .isInstanceOf(InitDataInvalidException.class)
                .hasMessageContaining("muddati");
    }

    @Test
    @DisplayName("hash yo'q — 401")
    void rejectsMissingHash() {
        assertThatThrownBy(() -> validator.validate("auth_date=1700000000&user=%7B%7D"))
                .isInstanceOf(InitDataInvalidException.class)
                .hasMessageContaining("hash");
    }

    @Test
    @DisplayName("Bo'sh initData — 401")
    void rejectsBlankInitData() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOf(InitDataInvalidException.class);
    }

    @Test
    @DisplayName("hash to'g'ri, lekin user maydoni yo'q — 401")
    void rejectsMissingUser() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("auth_date", String.valueOf(System.currentTimeMillis() / 1000));
        fields.put("chat_type", "private");
        String initData = InitDataFactory.build(BOT_TOKEN, fields);

        assertThatThrownBy(() -> validator.validate(initData))
                .isInstanceOf(InitDataInvalidException.class)
                .hasMessageContaining("foydalanuvchi");
    }

    @Test
    @DisplayName("hash hex emas — 401")
    void rejectsNonHexHash() {
        String initData = InitDataFactory.valid(BOT_TOKEN, TELEGRAM_ID);
        String broken = initData.substring(0, initData.lastIndexOf("hash=") + 5) + "zzzz";

        assertThatThrownBy(() -> validator.validate(broken))
                .isInstanceOf(InitDataInvalidException.class);
    }
}
