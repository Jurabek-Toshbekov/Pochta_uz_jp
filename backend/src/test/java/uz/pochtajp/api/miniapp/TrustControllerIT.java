package uz.pochtajp.api.miniapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.InitDataFactory;
import uz.pochtajp.support.PostFixtures;

/**
 * Shikoyat va baho endpoint'lari (§7.3, §6.4 7-band).
 *
 * <p>Asosiy qoida: bu ikkalasi ham qurol bo'lib qolmasligi kerak.
 * Shikoyat e'lonni yopmaydi, bahoni esa faqat bitimda qatnashgan odam
 * qoldira oladi.
 */
class TrustControllerIT extends AbstractIntegrationTest {

    private static final long OWNER_ID = 850_001L;
    private static final long VIEWER_ID = 850_002L;
    private static final long STRANGER_ID = 850_003L;

    private PostFixtures fixtures;

    private PostFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PostFixtures(jdbcTemplate);
        }
        return fixtures;
    }

    private String auth(long telegramId) {
        return "tma " + InitDataFactory.valid(TEST_BOT_TOKEN, telegramId);
    }

    /** Foydalanuvchini oldindan yaratadi va bazadagi ID'sini qaytaradi. */
    private UUID ensureUser(long telegramId, String username) {
        return fixtures().insertUser(telegramId, username, "NONE", 0);
    }

    private UUID userIdOf(long telegramId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE telegram_id = ?", UUID.class, telegramId);
    }

    private void revealContact(UUID postId, UUID viewerId, UUID ownerId) {
        jdbcTemplate.update("""
                INSERT INTO contact_reveals (post_id, viewer_id, owner_id, channel)
                VALUES (?, ?, ?, 'MINIAPP')
                """, postId, viewerId, ownerId);
    }

    // ------------------------------------------------------------------
    // Shikoyat
    // ------------------------------------------------------------------

    @Test
    @DisplayName("initData'siz shikoyat qabul qilinmaydi (§1.4)")
    void reportRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/miniapp/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + UUID.randomUUID() + "\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Shikoyat qabul qilinadi va event yoziladi")
    void acceptsReport() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "shikoyatchi");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reports")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"reason\":\"PROHIBITED\","
                                + "\"details\":\"Taqiqlangan buyum\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());

        Long reports = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reports WHERE post_id = ? AND status = 'OPEN'",
                Long.class, postId);
        assertThat(reports).isEqualTo(1);

        Long events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'report_submitted'", Long.class);
        assertThat(events).isEqualTo(1);
    }

    @Test
    @DisplayName("Shikoyat e'lonni yopmaydi — qaror moderatorniki (§11.2)")
    void reportDoesNotClosePost() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "shikoyatchi");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reports")
                .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postId\":\"" + postId + "\",\"reason\":\"SPAM\"}"));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, postId);
        assertThat(status).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Takroriy shikoyat qabul qilinmaydi")
    void rejectsDuplicateReport() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "shikoyatchi");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();
        String body = "{\"postId\":\"" + postId + "\",\"reason\":\"SPAM\"}";

        mockMvc.perform(post("/api/miniapp/reports")
                .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(body));

        mockMvc.perform(post("/api/miniapp/reports")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("O'z e'loniga shikoyat qilib bo'lmaydi")
    void cannotReportOwnPost() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reports")
                        .header(HttpHeaders.AUTHORIZATION, auth(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Noma'lum sabab rad etiladi")
    void rejectsUnknownReason() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "shikoyatchi");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reports")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"reason\":\"NOMALUM\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Baho
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Kontakt ochgan odam e'lon egasini baholaydi va ball o'sadi")
    void revealerCanReviewOwner() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "mijoz");
        UUID viewerId = userIdOf(VIEWER_ID);
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();
        revealContact(postId, viewerId, ownerId);

        mockMvc.perform(post("/api/miniapp/reviews")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"rating\":5,"
                                + "\"comment\":\"Vaqtida yetkazdi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trustScore").isNumber());

        Integer score = jdbcTemplate.queryForObject(
                "SELECT trust_score FROM users WHERE id = ?", Integer.class, ownerId);
        assertThat(score).isGreaterThan(0);

        Long events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'review_left'", Long.class);
        assertThat(events).isEqualTo(1);
    }

    @Test
    @DisplayName("Kontakt ochmagan odam baho qoldira olmaydi")
    void strangerCannotReview() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(STRANGER_ID, "begona");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reviews")
                        .header(HttpHeaders.AUTHORIZATION, auth(STRANGER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"rating\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bir e'longa ikki marta baho berib bo'lmaydi")
    void rejectsDuplicateReview() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "mijoz");
        UUID viewerId = userIdOf(VIEWER_ID);
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();
        revealContact(postId, viewerId, ownerId);
        String body = "{\"postId\":\"" + postId + "\",\"rating\":4}";

        mockMvc.perform(post("/api/miniapp/reviews")
                .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                .contentType(MediaType.APPLICATION_JSON).content(body));

        mockMvc.perform(post("/api/miniapp/reviews")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Baho 1..5 oralig'idan tashqarida bo'lsa 400")
    void rejectsOutOfRangeRating() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "mijoz");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reviews")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"rating\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("E'lon egasi sherik aniqlanmagan bo'lsa baho qoldira olmaydi")
    void ownerNeedsCounterpart() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();

        mockMvc.perform(post("/api/miniapp/reviews")
                        .header(HttpHeaders.AUTHORIZATION, auth(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\",\"rating\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Xabarnoma ochilishi belgilanadi")
    void marksNotificationOpened() throws Exception {
        UUID ownerId = ensureUser(OWNER_ID, "egasi");
        ensureUser(VIEWER_ID, "obunachi");
        UUID viewerId = userIdOf(VIEWER_ID);
        UUID postId = fixtures().post(ownerId).status("PUBLISHED").insert();
        jdbcTemplate.update("""
                INSERT INTO notifications_sent (user_id, post_id, status, kind)
                VALUES (?, ?, 'SENT', 'MATCH')
                """, viewerId, postId);

        mockMvc.perform(post("/api/miniapp/notifications/opened")
                        .header(HttpHeaders.AUTHORIZATION, auth(VIEWER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"" + postId + "\"}"))
                .andExpect(status().isNoContent());

        Long opened = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications_sent WHERE opened_at IS NOT NULL", Long.class);
        assertThat(opened).isEqualTo(1);
    }
}
