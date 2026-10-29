package uz.pochtajp.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.support.AbstractAdminIntegrationTest;

/**
 * Moderatsiya ro'yxatining kengaytirilgan filtrlari va saralashi.
 */
class AdminPostFilterIT extends AbstractAdminIntegrationTest {

    private String token;
    private UUID alice;
    private UUID bob;
    private UUID medicinePost;
    private UUID kixPost;

    @BeforeEach
    void seed() throws Exception {
        token = tokenFor(920001L, "MODERATOR");
        alice = fixtures().insertUser(920101L, "alice_f", "NONE", 0);
        bob = fixtures().insertUser(920102L, "bob_f", "NONE", 0);
        Short medicine = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo_categories WHERE code = 'MEDICINE'", Short.class);
        Short documents = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo_categories WHERE code = 'DOCUMENTS'", Short.class);

        medicinePost = fixtures().post(alice).route("NRT", "TAS").categories(medicine).insert();
        kixPost = fixtures().post(bob).route("KIX", "SKD").categories(documents)
                .departDate(LocalDate.now().plusDays(2)).insert();
        jdbcTemplate.update("UPDATE posts SET view_count = 42, created_at = now() - interval '10 days'"
                + " WHERE id = ?", kixPost);
        jdbcTemplate.update("""
                INSERT INTO reports (post_id, reporter_id, reason) VALUES (?, ?, 'SPAM')
                """, kixPost, alice);
    }

    @Test
    @DisplayName("Aeroport, kategoriya va risk bo'yicha filtr")
    void filtersByRouteCategoryAndRisk() throws Exception {
        mockMvc.perform(get("/api/admin/posts").param("origin", "kix").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(kixPost.toString()));

        mockMvc.perform(get("/api/admin/posts").param("risk", "HIGH").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(medicinePost.toString()));
    }

    @Test
    @DisplayName("Faqat shikoyat qilinganlar, foydalanuvchi va sana oralig'i bo'yicha filtr")
    void filtersByReportsUserAndDates() throws Exception {
        mockMvc.perform(get("/api/admin/posts").param("reportedOnly", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(kixPost.toString()));

        mockMvc.perform(get("/api/admin/posts").param("userId", alice.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(medicinePost.toString()));

        mockMvc.perform(get("/api/admin/posts")
                        .param("from", LocalDate.now().minusDays(1).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(medicinePost.toString()));
    }

    @Test
    @DisplayName("Saralash: ko'rishlar bo'yicha; noto'g'ri qiymat 400")
    void sortsAndRejectsUnknownSort() throws Exception {
        mockMvc.perform(get("/api/admin/posts").param("sort", "VIEWS").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.items[0].id").value(kixPost.toString()));

        mockMvc.perform(get("/api/admin/posts").param("sort", "DROP TABLE").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Reference ro'yxati admin uchun ochiq")
    void exposesReference() throws Exception {
        mockMvc.perform(get("/api/admin/reference").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.airports[0].code").exists())
                .andExpect(jsonPath("$.categories[0].id").exists());
    }
}
