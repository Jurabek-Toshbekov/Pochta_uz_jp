package uz.pochtajp.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.support.AbstractAdminIntegrationTest;

/**
 * Foydalanuvchi profili analitikasi: kim kimning e'lonini ko'rdi.
 */
class AdminUserAnalyticsIT extends AbstractAdminIntegrationTest {

    private void view(UUID viewer, UUID postId, Instant when) {
        jdbcTemplate.update("""
                INSERT INTO events (event_name, user_id, post_id, session_id, source, platform, occurred_at)
                VALUES ('post_detail_view', ?, ?, ?, 'MINIAPP', 'ios', ?)
                """, viewer, postId, UUID.randomUUID(), Timestamp.from(when));
    }

    @Test
    @DisplayName("Ko'rgan e'lonlar, ko'ruvchilar va jami ko'rsatkichlar to'g'ri hisoblanadi")
    void buildsViewGraph() throws Exception {
        String token = tokenFor(930001L, "MODERATOR");
        UUID owner = fixtures().insertUser(930101L, "egasi_a", "NONE", 0);
        UUID visitor = fixtures().insertUser(930102L, "mehmon_a", "NONE", 0);
        UUID ownerPost = fixtures().post(owner).insert();
        UUID visitorPost = fixtures().post(visitor).route("KIX", "SKD").insert();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        view(visitor, ownerPost, now.minusSeconds(120));
        view(visitor, ownerPost, now.minusSeconds(60));
        view(owner, ownerPost, now);              // o'z e'lonini ko'rishi hisobga olinmaydi
        view(owner, visitorPost, now);
        jdbcTemplate.update("""
                INSERT INTO contact_reveals (post_id, viewer_id, owner_id, channel) VALUES (?, ?, ?, 'MINIAPP')
                """, ownerPost, visitor, owner);

        mockMvc.perform(get("/api/admin/users/" + owner + "/analytics").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.username").value("egasi_a"))
                .andExpect(jsonPath("$.origin.platforms[0]").value("ios"))
                .andExpect(jsonPath("$.totals.viewsReceived").value(2))
                .andExpect(jsonPath("$.totals.uniqueViewers").value(1))
                .andExpect(jsonPath("$.totals.postsViewed").value(1))
                .andExpect(jsonPath("$.totals.revealsReceived").value(1))
                .andExpect(jsonPath("$.viewers.length()").value(1))
                .andExpect(jsonPath("$.viewers[0].username").value("mehmon_a"))
                .andExpect(jsonPath("$.viewers[0].views").value(2))
                .andExpect(jsonPath("$.viewers[0].contactReveals").value(1))
                .andExpect(jsonPath("$.viewedPosts.length()").value(1))
                .andExpect(jsonPath("$.viewedPosts[0].postId").value(visitorPost.toString()))
                .andExpect(jsonPath("$.viewedPosts[0].ownerUsername").value("mehmon_a"))
                .andExpect(jsonPath("$.posts[0].uniqueViewers").value(1))
                .andExpect(jsonPath("$.eventBreakdown[0].eventName").value("post_detail_view"));

        mockMvc.perform(get("/api/admin/users/" + visitor + "/analytics").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.viewedPosts[0].postId").value(ownerPost.toString()))
                .andExpect(jsonPath("$.viewedPosts[0].views").value(2))
                .andExpect(jsonPath("$.viewedPosts[0].contactRevealed").value(true))
                .andExpect(jsonPath("$.totals.revealsMade").value(1));

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'USER_ANALYTICS_VIEW' AND entity_id = ?",
                Integer.class, owner.toString());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    @DisplayName("Noma'lum foydalanuvchi — 404, oddiy foydalanuvchi — kirish yo'q")
    void guardsAccess() throws Exception {
        String token = tokenFor(930002L, "MODERATOR");
        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID() + "/analytics")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID() + "/analytics"))
                .andExpect(status().isUnauthorized());
    }
}
