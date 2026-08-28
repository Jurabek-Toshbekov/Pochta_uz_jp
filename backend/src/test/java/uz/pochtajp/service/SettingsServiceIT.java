package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.config.SettingKeys;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * Feature flag'lar (§11.2) va ularning haqiqiy ta'siri.
 *
 * <p>Eng muhim tekshiruv: {@code moderation.required} yoqilganda e'lon
 * kanalga chiqmaydi va {@code PENDING} holatida qoladi — ya'ni flag
 * bezak emas, chinakam ishlaydi.
 */
class SettingsServiceIT extends AbstractIntegrationTest {

    private static final long TELEGRAM_ID = 771_000_555L;

    @Autowired
    private SettingsService settingsService;

    @AfterEach
    void clearSettingsCache() {
        // Kesh instansiya darajasida yashaydi — keyingi testga oqib ketmasin.
        settingsService.refreshCache();
        jdbcTemplate.update("UPDATE app_settings SET value_json = 'false'::jsonb "
                + "WHERE setting_key = ?", SettingKeys.MODERATION_REQUIRED);
    }

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    private String body() {
        String tomorrow = DateTimeFormatter.ISO_LOCAL_DATE
                .format(LocalDate.now(ZoneOffset.UTC).plusDays(1));
        return """
                {
                  "postType": "CARRY",
                  "direction": "JP_UZ",
                  "originAirport": "NRT",
                  "destAirport": "TAS",
                  "departDate": "%s",
                  "weightKg": 5,
                  "priceAmount": 2000,
                  "priceCurrency": "JPY",
                  "priceUnit": "PER_KG",
                  "categoryIds": [1],
                  "contactTelegram": "@testuser",
                  "safetyChecklistOk": true,
                  "platform": "ios"
                }""".formatted(tomorrow);
    }

    @Test
    @DisplayName("Standart holatda moderatsiya o'chiq — e'lon darhol kanalga chiqadi")
    void publishesImmediatelyByDefault() throws Exception {
        mockMvc.perform(post("/api/miniapp/posts")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(channelPublisher.sentMessages()).hasSize(1);
    }

    @Test
    @DisplayName("moderation.required yoqilsa e'lon PENDING qoladi va kanalga chiqmaydi")
    void keepsPostPendingWhenModerationRequired() throws Exception {
        settingsService.update(SettingKeys.MODERATION_REQUIRED, true, null);

        mockMvc.perform(post("/api/miniapp/posts")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(channelPublisher.sentMessages()).isEmpty();

        // Ma'lumot saqlanadi (§1.1) — e'lon bazada turadi.
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE status = 'PENDING' AND deleted_at IS NULL",
                Integer.class);
        assertThat(stored).isEqualTo(1);
    }

    @Test
    @DisplayName("Sozlama yo'q bo'lsa standart qiymat ishlatiladi")
    void fallsBackWhenSettingMissing() {
        assertThat(settingsService.flag("mavjud.emas", true)).isTrue();
        assertThat(settingsService.number("mavjud.emas.ham", 42)).isEqualTo(42);
    }

    @Test
    @DisplayName("Raqamli sozlama o'qiladi")
    void readsNumberSetting() {
        assertThat(settingsService.number(SettingKeys.RATE_LIMIT_POSTS_PER_DAY, 1)).isEqualTo(5);
    }

    @Test
    @DisplayName("BOOLEAN kalitiga son yozib bo'lmaydi")
    void enforcesBooleanType() {
        assertThatThrownBy(() ->
                settingsService.update(SettingKeys.MODERATION_REQUIRED, 5, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("NUMBER kalitiga matn yozib bo'lmaydi")
    void enforcesNumberType() {
        assertThatThrownBy(() ->
                settingsService.update(SettingKeys.RATE_LIMIT_POSTS_PER_DAY, "ko'p", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Mavjud bo'lmagan sozlamani o'zgartirib bo'lmaydi")
    void rejectsUnknownKey() {
        assertThatThrownBy(() -> settingsService.update("yolgon.kalit", true, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("O'zgarish audit jurnaliga tushadi")
    void logsChangeToAudit() {
        UUID actor = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, telegram_id, username, role)
                VALUES (?, 771000999, 'admin_test', 'ADMIN')
                """, actor);

        settingsService.update(SettingKeys.VIP_ENABLED, true, actor);

        Integer rows = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action = 'SETTING_UPDATE' AND entity_id = ?
                """, Integer.class, SettingKeys.VIP_ENABLED);
        assertThat(rows).isEqualTo(1);

        jdbcTemplate.update("UPDATE app_settings SET value_json = 'false'::jsonb "
                + "WHERE setting_key = ?", SettingKeys.VIP_ENABLED);
    }
}
