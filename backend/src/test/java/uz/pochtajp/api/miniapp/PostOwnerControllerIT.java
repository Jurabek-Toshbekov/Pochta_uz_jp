package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;

/**
 * E'lon egasining harakatlari: {@code PATCH /api/miniapp/posts/{id}} va
 * {@code POST /api/miniapp/posts/{id}/close} (§9.1, §12).
 *
 * <p>Ikki narsa alohida tekshiriladi: tahrir kanalga yetib boradimi (eski
 * narx kanalda qolsa odam noto'g'ri ma'lumot bilan bog'lanadi) va yopish
 * sababi ajratib saqlanadimi (§6.4, 3-band — "javob bo'lmadi" mahsulot
 * muammosining yagona signali).
 */
class PostOwnerControllerIT extends AbstractIntegrationTest {

    private static final long TELEGRAM_ID = 424_000_222L;

    private String auth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID);
    }

    private String otherAuth() {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, TELEGRAM_ID + 1);
    }

    private static String date(int plusDays) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now(ZoneOffset.UTC).plusDays(plusDays));
    }

    /** E'lon yaratadi va uning id'sini qaytaradi. */
    private String createPost() throws Exception {
        String body = """
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
                  "categoryIds": [1, 2],
                  "comment": "Hujjat va kiyim olib ketaman",
                  "contactTelegram": "@testuser",
                  "contactPhone": "+998901234567",
                  "safetyChecklistOk": true
                }""".formatted(date(7));

        String response = mockMvc.perform(post("/api/miniapp/posts")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        channelPublisher.reset();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions edit(String postId, String auth, String body) throws Exception {
        return mockMvc.perform(patch("/api/miniapp/posts/" + postId)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions close(String postId, String auth, String body) throws Exception {
        return mockMvc.perform(post("/api/miniapp/posts/" + postId + "/close")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<String> eventNames() {
        return jdbcTemplate.queryForList("SELECT event_name FROM events ORDER BY id", String.class);
    }

    // ------------------------------------------------------------------
    // Tahrirlash
    // ------------------------------------------------------------------

    @Test
    @DisplayName("initData'siz tahrir — 401 (§1.4)")
    void rejectsEditWithoutInitData() throws Exception {
        String postId = createPost();

        mockMvc.perform(patch("/api/miniapp/posts/" + postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceAmount\": 3000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Narx tahrirlanadi — javobda yangi narx, kanaldagi post yangilanadi")
    void updatesPriceAndChannelMessage() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"priceAmount\": 3500, \"comment\": \"Faqat hujjat\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(3500))
                .andExpect(jsonPath("$.comment").value("Faqat hujjat"));

        assertThat(channelPublisher.editedMessages()).hasSize(1);
        assertThat(channelPublisher.editedMessages().get(0))
                .contains("3500 JPY / kg")
                .contains("Faqat hujjat")
                .doesNotContain("2000 JPY");
    }

    @Test
    @DisplayName("post_edit eventi o'zgargan maydonlar bilan yoziladi (§1.6, §6.1)")
    void writesEditEventWithChangedFields() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"priceAmount\": 3500, \"weightKg\": 12}")
                .andExpect(status().isOk());

        assertThat(eventNames()).contains("post_edit");
        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'post_edit'", String.class);
        assertThat(properties)
                .contains("changed_fields")
                .contains("priceAmount")
                .contains("weightKg");

        String source = jdbcTemplate.queryForObject(
                "SELECT source FROM events WHERE event_name = 'post_edit'", String.class);
        assertThat(source).isEqualTo("MINIAPP");
    }

    @Test
    @DisplayName("O'zgarish yo'q — event yozilmaydi, kanalga tegilmaydi")
    void noOpEditWritesNothing() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"priceAmount\": 2000, \"comment\": \"Hujjat va kiyim olib ketaman\"}")
                .andExpect(status().isOk());

        assertThat(eventNames()).doesNotContain("post_edit");
        assertThat(channelPublisher.editedMessages()).isEmpty();
    }

    @Test
    @DisplayName("2000 va 2000.00 bir xil narx — tahrir hisoblanmaydi")
    void ignoresScaleOnlyPriceChange() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"priceAmount\": 2000.00}").andExpect(status().isOk());

        assertThat(eventNames()).doesNotContain("post_edit");
    }

    @Test
    @DisplayName("Begonaning e'loni — 404, mavjudligi oshkor qilinmaydi")
    void hidesOtherUsersPostOnEdit() throws Exception {
        String postId = createPost();

        edit(postId, otherAuth(), "{\"priceAmount\": 1}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT price_amount FROM posts", java.math.BigDecimal.class))
                .isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("O'tib ketgan sana — 400, matn nima qilishni aytadi (§9.4)")
    void rejectsPastDepartDate() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"departDate\": \"%s\"}".formatted(date(-1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.departDate")
                        .value(Matchers.containsString("Bugundan keyingi kunni tanlang")));
    }

    @Test
    @DisplayName("Sana o'zgarsa muddat ham qayta hisoblanadi")
    void recomputesExpiryWhenDateChanges() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"departDate\": \"%s\", \"dateFlexibleDays\": 3}".formatted(date(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departDate").value(date(20)));

        java.sql.Timestamp expires = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM posts", java.sql.Timestamp.class);
        // Uchish sanasi + 3 kun moslashuv + 1 kun zapas
        assertThat(expires.toInstant())
                .isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(23).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    @DisplayName("Kategoriyalar almashadi — faqat farq o'zgaradi")
    void replacesCategories() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"categoryIds\": [2, 3]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds.length()").value(2));

        List<Integer> ids = jdbcTemplate.queryForList(
                "SELECT category_id FROM post_categories ORDER BY category_id", Integer.class);
        assertThat(ids).containsExactly(2, 3);
    }

    @Test
    @DisplayName("Bo'sh kategoriya ro'yxati — 400")
    void rejectsEmptyCategories() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"categoryIds\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryIds").exists());
    }

    @Test
    @DisplayName("Ro'yxatda yo'q kategoriya — 400")
    void rejectsUnknownCategory() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"categoryIds\": [999]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryIds").exists());
    }

    @Test
    @DisplayName("\"Kelishamiz\"ga o'tsa summa tozalanadi (§6.3 narx indeksi)")
    void negotiableClearsAmount() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"priceUnit\": \"NEGOTIABLE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").doesNotExist())
                .andExpect(jsonPath("$.priceCurrency").doesNotExist());

        assertThat(channelPublisher.editedMessages().get(0)).contains("kelishamiz");
    }

    @Test
    @DisplayName("Tahrirda Telegram'ni o'chirish — 400")
    void keepsTelegramRequiredOnEdit() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"contactTelegram\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactTelegram").exists());
    }

    /** Tahrir orqali qoidani chetlab o'tish yo'li ochiq qolmasligi kerak. */
    @Test
    @DisplayName("Tahrirda telefonni o'chirish — 400")
    void keepsPhoneRequiredOnEdit() throws Exception {
        String postId = createPost();

        edit(postId, auth(), "{\"contactPhone\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT contact_phone FROM posts", String.class)).isEqualTo("+998901234567");
    }

    @Test
    @DisplayName("Kanal javob bermasa ham tahrir saqlanadi")
    void keepsEditWhenChannelFails() throws Exception {
        String postId = createPost();
        channelPublisher.failNextRequests();

        edit(postId, auth(), "{\"priceAmount\": 4000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(4000));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT price_amount FROM posts", java.math.BigDecimal.class))
                .isEqualByComparingTo("4000");
    }

    @Test
    @DisplayName("Yopilgan e'lon tahrirlanmaydi — 403")
    void rejectsEditOfClosedPost() throws Exception {
        String postId = createPost();
        close(postId, auth(), "{\"reason\": \"CANCELLED\"}").andExpect(status().isOk());

        edit(postId, auth(), "{\"priceAmount\": 4000}")
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Yopish (§6.4, 3-band)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FOUND — e'lon yopiladi, bitim tasdiqlanadi, event'lar yoziladi")
    void closesAsFound() throws Exception {
        String postId = createPost();

        close(postId, auth(), "{\"reason\": \"FOUND\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT closed_reason FROM posts", String.class)).isEqualTo("FOUND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deal_confirmed_at FROM posts", java.sql.Timestamp.class)).isNotNull();
        assertThat(eventNames()).contains("post_close", "deal_confirmed", "deal_followup_answer");
    }

    @Test
    @DisplayName("NO_ANSWER alohida saqlanadi — \"bekor qilindi\" bilan qo'shilmaydi (§6.4)")
    void closesAsNoAnswer() throws Exception {
        String postId = createPost();

        close(postId, auth(), "{\"reason\": \"NO_ANSWER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT closed_reason FROM posts", String.class)).isEqualTo("NO_ANSWER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deal_confirmed_at FROM posts", java.sql.Timestamp.class)).isNull();

        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'post_close'", String.class);
        assertThat(properties).contains("NO_ANSWER").contains("hours_since_publish");
    }

    @Test
    @DisplayName("Yopish manbai MINIAPP deb yoziladi — bot javobidan ajratiladi")
    void recordsMiniAppSource() throws Exception {
        String postId = createPost();

        close(postId, auth(), "{\"reason\": \"CANCELLED\"}").andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT source FROM events WHERE event_name = 'post_close'", String.class))
                .isEqualTo("MINIAPP");
    }

    @Test
    @DisplayName("Sabab berilmasa — 400 (sabab majburiy)")
    void requiresReason() throws Exception {
        String postId = createPost();

        close(postId, auth(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    @Test
    @DisplayName("Ikki marta yopish — holat o'zgarmaydi, ikkinchi event qo'shilmaydi")
    void closingTwiceIsIdempotent() throws Exception {
        String postId = createPost();

        close(postId, auth(), "{\"reason\": \"CANCELLED\"}").andExpect(status().isOk());
        close(postId, auth(), "{\"reason\": \"FOUND\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT closed_reason FROM posts", String.class)).isEqualTo("CANCELLED");
        Integer closeEvents = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'post_close'", Integer.class);
        assertThat(closeEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("Begonaning e'lonini yopib bo'lmaydi — 404")
    void hidesOtherUsersPostOnClose() throws Exception {
        String postId = createPost();

        close(postId, otherAuth(), "{\"reason\": \"FOUND\"}")
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM posts", String.class)).isEqualTo("PUBLISHED");
    }
}
