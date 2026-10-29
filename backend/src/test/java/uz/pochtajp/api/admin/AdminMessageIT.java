package uz.pochtajp.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import uz.pochtajp.bot.BotUpdateHandler;
import uz.pochtajp.support.AbstractAdminIntegrationTest;
import uz.pochtajp.support.BotUpdates;

/**
 * Admin ↔ foydalanuvchi yozishmasi: panel -> bot -> foydalanuvchi -> bot -> panel.
 */
class AdminMessageIT extends AbstractAdminIntegrationTest {

    private static final long USER_TG = 940_100L;

    @Autowired
    private BotUpdateHandler botHandler;

    private UUID user() {
        return fixtures().insertUser(USER_TG, "yozuvchi", "NONE", 0);
    }

    private void send(String token, UUID userId, String text) throws Exception {
        mockMvc.perform(post("/api/admin/users/" + userId + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("text", text))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin xabari bot orqali yetkaziladi, HTML escape qilinadi va saqlanadi")
    void sendsThroughBot() throws Exception {
        String token = tokenFor(940001L, "MODERATOR");
        UUID userId = user();

        mockMvc.perform(post("/api/admin/users/" + userId + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Narxni <b>to'g'ri</b> yozing & qayta yuboring\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.deliveryStatus").value("SENT"));

        var sent = botMessenger.lastMessage();
        assertThat(sent.chatId()).isEqualTo(USER_TG);
        assertThat(sent.text()).contains("Ma'muriyatdan xabar")
                .contains("&lt;b&gt;to'g'ri&lt;/b&gt; yozing &amp; qayta");

        Integer audits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'ADMIN_MESSAGE_SEND' AND entity_id = ?",
                Integer.class, userId.toString());
        assertThat(audits).isEqualTo(1);
    }

    @Test
    @DisplayName("Bot bloklangan bo'lsa xabar FAILED bilan saqlanadi va suhbat ochilmaydi")
    void failedDeliveryIsRecorded() throws Exception {
        String token = tokenFor(940002L, "MODERATOR");
        UUID userId = user();
        botMessenger.failSends();

        mockMvc.perform(post("/api/admin/users/" + userId + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Salom\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("FAILED"));

        botMessenger.reset();
        botHandler.handle(BotUpdates.text("Salom, nima gap?", USER_TG, "uz"));
        Integer inbound = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_messages WHERE user_id = ? AND direction = 'INBOUND'",
                Integer.class, userId);
        assertThat(inbound).isZero();
    }

    @Test
    @DisplayName("Foydalanuvchi javobi panelda ko'rinadi, o'qilmaganlar hisoblanadi va o'qiladi")
    void replyShowsUpInPanel() throws Exception {
        String token = tokenFor(940003L, "MODERATOR");
        UUID userId = user();
        send(token, userId, "E'loningizdagi sana o'tib ketgan, yangilang.");

        botMessenger.reset();
        botHandler.handle(BotUpdates.text("Rahmat, tuzataman", USER_TG, "uz"));
        assertThat(botMessenger.lastMessage().text()).contains("yetkazildi");

        // Buyruqlar suhbat ochiq bo'lsa ham odatdagidek ishlaydi.
        botHandler.handle(BotUpdates.text("/yordam", USER_TG, "uz"));

        mockMvc.perform(get("/api/admin/users/" + userId + "/messages").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$[1].direction").value("INBOUND"))
                .andExpect(jsonPath("$[1].body").value("Rahmat, tuzataman"));

        mockMvc.perform(get("/api/admin/messages/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/admin/messages").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$[0].unreadCount").value(1))
                .andExpect(jsonPath("$[0].lastBody").value("Rahmat, tuzataman"));

        mockMvc.perform(post("/api/admin/users/" + userId + "/messages/read").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.marked").value(1));
        mockMvc.perform(get("/api/admin/messages/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("Suhbat bo'lmasa erkin matn saqlanmaydi; media suhbatda faqat belgi sifatida")
    void freeTextWithoutConversation() throws Exception {
        UUID userId = fixtures().insertUser(BotUpdates.TELEGRAM_ID, "mehmon", "NONE", 0);
        botHandler.handle(BotUpdates.text("salom"));
        assertThat(botMessenger.lastMessage().text()).doesNotContain("yetkazildi");

        String token = tokenFor(940004L, "MODERATOR");
        send(token, userId, "Rasmni ilova orqali emas, matn bilan yozing.");
        botHandler.handle(BotUpdates.photo());
        String stored = jdbcTemplate.queryForObject(
                "SELECT body FROM admin_messages WHERE user_id = ? AND direction = 'INBOUND'",
                String.class, userId);
        assertThat(stored).startsWith("📎 [media]");
    }

    @Test
    @DisplayName("Bo'sh xabar — 400, noma'lum foydalanuvchi — 404")
    void validates() throws Exception {
        String token = tokenFor(940005L, "MODERATOR");
        mockMvc.perform(post("/api/admin/users/" + user() + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/users/" + UUID.randomUUID() + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Salom\"}"))
                .andExpect(status().isNotFound());
    }
}
