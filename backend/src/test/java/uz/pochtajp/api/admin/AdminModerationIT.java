package uz.pochtajp.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.pochtajp.support.AbstractAdminIntegrationTest;

/**
 * Moderatsiya oqimi (§11.2 — /posts, /users, /reports).
 *
 * <p>Asosiy qoidalar: hech narsa o'chirilmaydi (§1.1), har bir harakat
 * jurnalga tushadi, sabab majburiy.
 */
class AdminModerationIT extends AbstractAdminIntegrationTest {

    private UUID pendingPost(UUID ownerId) {
        return fixtures().post(ownerId)
                .type("CARRY")
                .direction("JP_UZ")
                .route("NRT", "TAS")
                .departDate(LocalDate.now().plusDays(10))
                .price("2000", "JPY", "PER_KG")
                .status("PENDING")
                .insert();
    }

    @Test
    @DisplayName("E'lonlar ro'yxati filtr bilan qaytadi")
    void listsPostsWithFilter() throws Exception {
        String token = tokenFor(910001L, "MODERATOR");
        UUID owner = fixtures().insertUser(910101L, "egasi", "NONE", 0);
        pendingPost(owner);

        mockMvc.perform(get("/api/admin/posts").param("status", "PENDING")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].originAirport").value("NRT"));
    }

    @Test
    @DisplayName("Tasdiqlash e'lonni kanalga chiqaradi va jurnalga yozadi")
    void approvePublishesAndLogs() throws Exception {
        String token = tokenFor(910002L, "MODERATOR");
        UUID owner = fixtures().insertUser(910102L, "egasi2", "NONE", 0);
        UUID postId = pendingPost(owner);

        mockMvc.perform(post("/api/admin/posts/" + postId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("PUBLISHED"));

        Integer moderationRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM moderation_actions WHERE target_id = ? AND action = 'APPROVE'",
                Integer.class, postId);
        assertThat(moderationRows).isEqualTo(1);

        Integer auditRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'POST_APPROVE' AND entity_id = ?",
                Integer.class, postId.toString());
        assertThat(auditRows).isEqualTo(1);
    }

    @Test
    @DisplayName("Rad etishda sabab majburiy")
    void rejectRequiresReason() throws Exception {
        String token = tokenFor(910003L, "MODERATOR");
        UUID owner = fixtures().insertUser(910103L, "egasi3", "NONE", 0);
        UUID postId = pendingPost(owner);

        mockMvc.perform(post("/api/admin/posts/" + postId + "/reject")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rad etilgan e'lon o'chirilmaydi, sababi saqlanadi (§1.1)")
    void rejectKeepsRow() throws Exception {
        String token = tokenFor(910004L, "MODERATOR");
        UUID owner = fixtures().insertUser(910104L, "egasi4", "NONE", 0);
        UUID postId = pendingPost(owner);

        mockMvc.perform(post("/api/admin/posts/" + postId + "/reject")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Taqiqlangan buyum\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("REJECTED"))
                .andExpect(jsonPath("$.row.rejectReason").value("Taqiqlangan buyum"));

        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts WHERE id = ? AND deleted_at IS NULL", Integer.class, postId);
        assertThat(stillThere).isEqualTo(1);

        // §6.1 — moderatsiya qarori event sifatida ham yoziladi.
        Integer events = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = 'post_rejected' AND post_id = ?",
                Integer.class, postId);
        assertThat(events).isEqualTo(1);
    }

    @Test
    @DisplayName("Tafsilotda telefon maskalangan holda keladi (§1.7)")
    void detailMasksPhone() throws Exception {
        String token = tokenFor(910005L, "MODERATOR");
        UUID owner = fixtures().insertUser(910105L, "egasi5", "NONE", 0);
        UUID postId = pendingPost(owner);
        jdbcTemplate.update("UPDATE posts SET contact_phone = '+998901234567' WHERE id = ?", postId);

        mockMvc.perform(get("/api/admin/posts/" + postId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPhoneMasked").value("********4567"));
    }

    @Test
    @DisplayName("Foydalanuvchini bloklash sabab bilan yoziladi")
    void blocksUser() throws Exception {
        String token = tokenFor(910006L, "MODERATOR");
        UUID target = fixtures().insertUser(910106L, "buzuvchi", "NONE", 0);

        mockMvc.perform(post("/api/admin/users/" + target + "/block")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockedReason").value("Spam"));

        Integer logged = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM moderation_actions WHERE target_id = ? AND action = 'BLOCK'",
                Integer.class, target);
        assertThat(logged).isEqualTo(1);

        // §6.1 — event'da faqat ID, sabab matni emas (§1.7).
        String properties = jdbcTemplate.queryForObject(
                "SELECT properties::text FROM events WHERE event_name = 'user_blocked'", String.class);
        assertThat(properties).contains(target.toString()).doesNotContain("Spam");
    }

    @Test
    @DisplayName("Tasdiqlash darajasi noto'g'ri bo'lsa 400")
    void rejectsUnknownVerificationLevel() throws Exception {
        String token = tokenFor(910007L, "MODERATOR");
        UUID target = fixtures().insertUser(910107L, "foydalanuvchi", "NONE", 0);

        mockMvc.perform(post("/api/admin/users/" + target + "/verify")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"SUPER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Shikoyat yakunlanadi va jurnalga tushadi")
    void resolvesReport() throws Exception {
        String token = tokenFor(910008L, "MODERATOR");
        UUID reporter = fixtures().insertUser(910108L, "shikoyatchi", "NONE", 0);
        UUID reported = fixtures().insertUser(910109L, "shikoyatlangan", "NONE", 0);
        UUID reportId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reports (id, reported_user_id, reporter_id, reason, details, status)
                VALUES (?, ?, ?, 'SPAM', 'Reklama yuboryapti', 'OPEN')
                """, reportId, reported, reporter);

        mockMvc.perform(post("/api/admin/reports/" + reportId + "/resolve")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"RESOLVED\",\"note\":\"Ogohlantirildi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        Integer audit = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'REPORT_RESOLVE'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("Moderator sozlamani o'zgartira olmaydi, ADMIN o'zgartira oladi")
    void onlyAdminChangesSettings() throws Exception {
        String moderatorToken = tokenFor(910009L, "MODERATOR");
        mockMvc.perform(patch("/api/admin/settings/vip.enabled")
                        .header("Authorization", bearer(moderatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isForbidden());

        String adminToken = tokenFor(910010L, "ADMIN");
        mockMvc.perform(patch("/api/admin/settings/vip.enabled")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(true));

        // Tozalab qo'yamiz: sozlama keshi testlar orasida yashaydi.
        mockMvc.perform(patch("/api/admin/settings/vip.enabled")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":false}"));
    }

    @Test
    @DisplayName("BOOLEAN sozlamaga raqam yozib bo'lmaydi")
    void settingTypeIsEnforced() throws Exception {
        String adminToken = tokenFor(910011L, "ADMIN");

        mockMvc.perform(patch("/api/admin/settings/moderation.required")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":42}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Audit jurnali o'qiladi")
    void readsAuditLog() throws Exception {
        String token = tokenFor(910012L, "ADMIN");
        UUID target = fixtures().insertUser(910112L, "kimdir", "NONE", 0);

        mockMvc.perform(post("/api/admin/users/" + target + "/block")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Test\"}"));

        mockMvc.perform(get("/api/admin/audit").param("action", "USER_BLOCK")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("USER_BLOCK"))
                .andExpect(jsonPath("$.items[0].entity").value("USER"));
    }
}
