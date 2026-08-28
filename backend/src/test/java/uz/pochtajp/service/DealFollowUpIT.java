package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.pochtajp.bot.BotUpdateHandler;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.BotUpdates;
import uz.pochtajp.support.PostFixtures;

/**
 * "Odam topdingizmi?" va muddat ogohlantirishi (§6.4, 1-band; §8.3).
 *
 * <p>Bu oqim loyihaning eng qimmatli ma'lumotini yig'adi — e'lon haqiqatan
 * ish berdimi. Shuning uchun har bir javob varianti alohida tekshiriladi.
 */
class DealFollowUpIT extends AbstractIntegrationTest {

    private static final long OWNER_TELEGRAM_ID = BotUpdates.TELEGRAM_ID;

    @Autowired
    private PostFollowUpJob followUpJob;

    @Autowired
    private BotUpdateHandler botHandler;

    private PostFixtures fixtures;

    private PostFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PostFixtures(jdbcTemplate);
        }
        return fixtures;
    }

    private UUID owner() {
        return fixtures().insertUser(OWNER_TELEGRAM_ID, "egasi", "NONE", 0);
    }

    /** N kun oldin publish bo'lgan e'lon. */
    private UUID oldPost(UUID ownerId, int daysAgo) {
        UUID postId = fixtures().post(ownerId)
                .status("PUBLISHED")
                .publishedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS))
                .insert();
        return postId;
    }

    private String status(UUID postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    private long eventCount(String name) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events WHERE event_name = ?", Long.class, name);
        return value == null ? 0 : value;
    }

    @Test
    @DisplayName("3 kundan oshgan e'lon uchun so'rov yuboriladi")
    void asksAfterThreeDays() {
        UUID ownerId = owner();
        oldPost(ownerId, 4);

        int sent = followUpJob.askAboutDeals();

        assertThat(sent).isEqualTo(1);
        assertThat(botMessenger.messages()).hasSize(1);
        assertThat(botMessenger.lastMessage().callbackData())
                .anyMatch(data -> data.startsWith("deal:found:"));
    }

    @Test
    @DisplayName("Yangi e'lon uchun so'rov yuborilmaydi")
    void doesNotAskTooEarly() {
        UUID ownerId = owner();
        oldPost(ownerId, 1);

        assertThat(followUpJob.askAboutDeals()).isZero();
        assertThat(botMessenger.messages()).isEmpty();
    }

    @Test
    @DisplayName("Job ikki marta ishlasa ham so'rov bir marta ketadi")
    void askIsIdempotent() {
        UUID ownerId = owner();
        oldPost(ownerId, 5);

        followUpJob.askAboutDeals();
        int second = followUpJob.askAboutDeals();

        assertThat(second).isZero();
        assertThat(botMessenger.messages()).hasSize(1);
    }

    @Test
    @DisplayName("\"Odam topildi\" e'lonni yopadi va bitimni yozadi")
    void foundClosesPostAndRecordsDeal() {
        UUID ownerId = owner();
        UUID postId = oldPost(ownerId, 4);

        botHandler.handle(BotUpdates.callback("deal:found:" + postId, OWNER_TELEGRAM_ID));

        assertThat(status(postId)).isEqualTo("CLOSED");
        String reason = jdbcTemplate.queryForObject(
                "SELECT closed_reason FROM posts WHERE id = ?", String.class, postId);
        assertThat(reason).isEqualTo("FOUND");

        Instant confirmed = jdbcTemplate.queryForObject(
                "SELECT deal_confirmed_at FROM posts WHERE id = ?", Instant.class, postId);
        assertThat(confirmed).isNotNull();

        assertThat(eventCount("deal_confirmed")).isEqualTo(1);
        assertThat(eventCount("post_close")).isEqualTo(1);
    }

    @Test
    @DisplayName("\"Hali javob yo'q\" e'lonni ochiq qoldiradi, lekin javob yoziladi")
    void notYetKeepsPostOpen() {
        UUID ownerId = owner();
        UUID postId = oldPost(ownerId, 4);

        botHandler.handle(BotUpdates.callback("deal:wait:" + postId, OWNER_TELEGRAM_ID));

        assertThat(status(postId)).isEqualTo("PUBLISHED");
        assertThat(eventCount("deal_followup_answer")).isEqualTo(1);
        assertThat(eventCount("deal_confirmed")).isZero();
    }

    @Test
    @DisplayName("\"Rejam o'zgardi\" e'lonni CANCELLED sababi bilan yopadi")
    void cancelledClosesWithReason() {
        UUID ownerId = owner();
        UUID postId = oldPost(ownerId, 4);

        botHandler.handle(BotUpdates.callback("deal:cancel:" + postId, OWNER_TELEGRAM_ID));

        String reason = jdbcTemplate.queryForObject(
                "SELECT closed_reason FROM posts WHERE id = ?", String.class, postId);
        assertThat(reason).isEqualTo("CANCELLED");
        assertThat(eventCount("deal_confirmed")).isZero();
    }

    @Test
    @DisplayName("Begona odam boshqaning e'lonini yopa olmaydi")
    void strangerCannotAnswer() {
        UUID ownerId = fixtures().insertUser(840001L, "boshqa_egasi", "NONE", 0);
        UUID postId = oldPost(ownerId, 4);

        botHandler.handle(BotUpdates.callback("deal:found:" + postId, OWNER_TELEGRAM_ID));

        assertThat(status(postId)).isEqualTo("PUBLISHED");
        assertThat(botMessenger.lastMessage().text()).contains("sizniki emas");
    }

    @Test
    @DisplayName("Buzilgan callback ma'lumoti xato bermaydi")
    void malformedCallbackIsIgnored() {
        owner();

        botHandler.handle(BotUpdates.callback("deal:found:not-a-uuid", OWNER_TELEGRAM_ID));

        assertThat(botMessenger.messages()).isEmpty();
    }

    @Test
    @DisplayName("Muddati tugayotgan e'lon egasiga ogohlantirish ketadi")
    void warnsBeforeExpiry() {
        UUID ownerId = owner();
        fixtures().post(ownerId)
                .status("PUBLISHED")
                .publishedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
                .insert();

        int sent = followUpJob.warnAboutExpiry();

        assertThat(sent).isEqualTo(1);
        assertThat(botMessenger.lastMessage().text()).contains("muddati");
    }

    @Test
    @DisplayName("Muddati uzoq e'lon ogohlantirilmaydi")
    void doesNotWarnTooEarly() {
        UUID ownerId = owner();
        fixtures().post(ownerId)
                .status("PUBLISHED")
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .insert();

        assertThat(followUpJob.warnAboutExpiry()).isZero();
    }
}
