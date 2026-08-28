package uz.pochtajp.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.pochtajp.service.DailyMetricsJob;
import uz.pochtajp.support.AbstractAdminIntegrationTest;

/**
 * Analitika (§6.3, §11.2).
 *
 * <p>Har bir metrika view'dan keladi — test view va SQL to'g'ri ishlashini
 * tekshiradi, aks holda dashboard jim yolg'on ko'rsatadi.
 */
class AdminAnalyticsIT extends AbstractAdminIntegrationTest {

    @Autowired
    private DailyMetricsJob dailyMetricsJob;

    private void insertEvent(UUID userId, String name, String propertiesJson, Instant when) {
        jdbcTemplate.update("""
                INSERT INTO events (event_name, user_id, source, properties, occurred_at)
                VALUES (?, ?, 'MINIAPP', ?::jsonb, ?)
                """, name, userId, propertiesJson, java.sql.Timestamp.from(when));
    }

    private void insertSearch(UUID userId, String origin, String dest, int resultCount) {
        jdbcTemplate.update("""
                INSERT INTO search_queries (user_id, post_type, direction, origin_airport,
                                            dest_airport, result_count, latency_ms)
                VALUES (?, 'CARRY', 'JP_UZ', ?, ?, ?, 12)
                """, userId, origin, dest, resultCount);
    }

    @Test
    @DisplayName("Overview asosiy raqamlarni qaytaradi")
    void overviewReturnsKpis() throws Exception {
        String token = tokenFor(920001L, "ADMIN");
        UUID owner = fixtures().insertUser(920101L, "egasi", "NONE", 0);
        fixtures().post(owner).status("PENDING").insert();

        mockMvc.perform(get("/api/admin/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postsToday").value(1))
                .andExpect(jsonPath("$.pendingPosts").value(1))
                .andExpect(jsonPath("$.openReports").value(0));
    }

    @Test
    @DisplayName("Voronka qadamlari tartib bilan va konversiya bilan keladi")
    void funnelIsOrderedWithConversion() throws Exception {
        String token = tokenFor(920002L, "ADMIN");
        UUID a = fixtures().insertUser(920102L, "a", "NONE", 0);
        UUID b = fixtures().insertUser(920103L, "b", "NONE", 0);
        Instant now = Instant.now();

        insertEvent(a, "post_form_open", "{}", now);
        insertEvent(b, "post_form_open", "{}", now);
        insertEvent(a, "post_form_step_complete", "{\"step_index\":\"1\"}", now);
        insertEvent(b, "post_form_step_complete", "{\"step_index\":\"1\"}", now);
        insertEvent(a, "post_publish_success", "{\"total_time_ms\":\"45000\"}", now);

        mockMvc.perform(get("/api/admin/analytics/funnel").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepKey").value("form_open"))
                .andExpect(jsonPath("$[0].usersCount").value(2))
                .andExpect(jsonPath("$[1].stepKey").value("step_1"))
                .andExpect(jsonPath("$[1].usersCount").value(2))
                .andExpect(jsonPath("$[2].stepKey").value("published"))
                .andExpect(jsonPath("$[2].usersCount").value(1));
    }

    @Test
    @DisplayName("Natijasiz qidiruvlar yo'nalish bo'yicha guruhlanadi")
    void zeroResultRoutesAreGrouped() throws Exception {
        String token = tokenFor(920003L, "ADMIN");
        UUID user = fixtures().insertUser(920104L, "qidiruvchi", "NONE", 0);

        insertSearch(user, "KIX", "SKD", 0);
        insertSearch(user, "KIX", "SKD", 0);
        insertSearch(user, "NRT", "TAS", 3);

        mockMvc.perform(get("/api/admin/search-insights/zero-results")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originAirport").value("KIX"))
                .andExpect(jsonPath("$[0].destAirport").value("SKD"))
                .andExpect(jsonPath("$[0].searchCount").value(2))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Talab va taklif yonma-yon ko'rinadi")
    void demandAndSupplySideBySide() throws Exception {
        String token = tokenFor(920004L, "ADMIN");
        UUID user = fixtures().insertUser(920105L, "u", "NONE", 0);
        insertSearch(user, "NRT", "TAS", 0);
        fixtures().post(user).route("NRT", "TAS").status("PUBLISHED")
                .publishedAt(Instant.now()).insert();

        mockMvc.perform(get("/api/admin/search-insights/demand-supply")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originAirport").value("NRT"))
                .andExpect(jsonPath("$[0].searchCount").value(1))
                .andExpect(jsonPath("$[0].postCount").value(1));
    }

    @Test
    @DisplayName("Narx indeksi medianani hisoblaydi")
    void priceIndexComputesMedian() throws Exception {
        String token = tokenFor(920005L, "ADMIN");
        UUID user = fixtures().insertUser(920106L, "narx", "NONE", 0);
        Instant published = Instant.now();

        fixtures().post(user).route("NRT", "TAS").price("1000", "JPY", "PER_KG")
                .status("PUBLISHED").publishedAt(published).insert();
        fixtures().post(user).route("NRT", "TAS").price("3000", "JPY", "PER_KG")
                .status("PUBLISHED").publishedAt(published).insert();

        mockMvc.perform(get("/api/admin/analytics/price-index").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sampleSize").value(2))
                .andExpect(jsonPath("$[0].medianPerKg").value(2000.00));
    }

    @Test
    @DisplayName("Kunlik agregat job'i yozadi va qayta ishga tushirilsa takrorlanmaydi")
    void dailyMetricsJobIsIdempotent() {
        UUID user = fixtures().insertUser(920107L, "agregat", "NONE", 0);
        fixtures().post(user).status("PENDING").insert();
        insertEvent(user, "post_form_open", "{}", Instant.now());
        LocalDate today = LocalDate.now();

        dailyMetricsJob.compute(today);
        Integer firstRun = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_metrics WHERE metric_date = ?", Integer.class, today);

        dailyMetricsJob.compute(today);
        Integer secondRun = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_metrics WHERE metric_date = ?", Integer.class, today);

        assertThat(firstRun).isPositive();
        assertThat(secondRun).isEqualTo(firstRun);

        java.math.BigDecimal created = jdbcTemplate.queryForObject("""
                SELECT value FROM daily_metrics
                WHERE metric_date = ? AND metric_key = 'posts_created' AND dimension = ''
                """, java.math.BigDecimal.class, today);
        assertThat(created).isNotNull();
        assertThat(created.intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("Moderator ham analitikani ko'ra oladi")
    void moderatorCanReadAnalytics() throws Exception {
        String token = tokenFor(920006L, "MODERATOR");

        mockMvc.perform(get("/api/admin/analytics/cohorts").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
