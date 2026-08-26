package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * {@code POST /session} va {@code GET /reference} (§12).
 */
class SessionAndReferenceIT extends AbstractIntegrationTest {

    private static final long TELEGRAM_ID = 616_000_222L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    @Test
    @DisplayName("Birinchi sessiya — isNewUser=true, rozilik so'raladi (§7.2)")
    void firstSessionRequiresConsent() throws Exception {
        mockMvc.perform(post("/api/miniapp/session")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.needsConsent").value(true))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.uiLanguage").value("uz"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Rozilik yozilgach needsConsent=false, vaqti qayta yozilmaydi")
    void storesConsentOnce() throws Exception {
        mockMvc.perform(post("/api/miniapp/session")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptTos\":true,\"acceptPrivacy\":true,\"uiLanguage\":\"ru\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsConsent").value(false))
                .andExpect(jsonPath("$.uiLanguage").value("ru"));

        String firstConsent = jdbcTemplate.queryForObject(
                "SELECT consent_tos_at::text FROM users WHERE telegram_id = ?", String.class, TELEGRAM_ID);

        mockMvc.perform(post("/api/miniapp/session")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptTos\":true,\"acceptPrivacy\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false));

        String secondConsent = jdbcTemplate.queryForObject(
                "SELECT consent_tos_at::text FROM users WHERE telegram_id = ?", String.class, TELEGRAM_ID);
        assertThat(secondConsent).isEqualTo(firstConsent);
    }

    @Test
    @DisplayName("Noto'g'ri til qiymati — 400")
    void rejectsUnknownLanguage() throws Exception {
        mockMvc.perform(post("/api/miniapp/session")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiLanguage\":\"en\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.uiLanguage").exists());
    }

    @Test
    @DisplayName("start_param sessiyada qaytadi — deep link marshrutga aylanadi (§9.3)")
    void returnsStartParam() throws Exception {
        String initData = InitDataFactory.validWithStartParam(TEST_BOT_TOKEN, TELEGRAM_ID, "ch_abc");

        mockMvc.perform(post("/api/miniapp/session")
                        .header(HttpHeaders.AUTHORIZATION, "tma " + initData)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startParam").value("ch_abc"));
    }

    @Test
    @DisplayName("Reference — aeroportlar, kategoriyalar, koridorlar")
    void returnsReferenceData() throws Exception {
        mockMvc.perform(get("/api/miniapp/reference").header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.airports.length()").value(12))
                .andExpect(jsonPath("$.categories.length()").value(7))
                .andExpect(jsonPath("$.corridors.length()").value(1))
                .andExpect(jsonPath("$.corridors[0].code").value("JP_UZ"));
    }

    @Test
    @DisplayName("HIGH risk kategoriyada ogohlantirish matni keladi (§7.3)")
    void highRiskCategoriesCarryWarning() throws Exception {
        mockMvc.perform(get("/api/miniapp/reference").header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.riskLevel == 'HIGH')].warningUz")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))));
    }

    @Test
    @DisplayName("Reference initData'siz olinmaydi (§1.4)")
    void referenceRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/miniapp/reference"))
                .andExpect(status().isUnauthorized());
    }
}
