package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.pochtajp.support.AbstractIntegrationTest;
import uz.pochtajp.support.PostFixtures;

/**
 * Ishonch balli — bazadagi haqiqiy ma'lumot bilan.
 *
 * <p>Formula unit testi ({@link TrustScoreServiceTest}) sof funksiyani
 * tekshiradi, bu esa <b>xom raqamlar to'g'ri o'qilayotganini</b>.
 *
 * <p>Nima uchun kerak: bir marta baho ballga umuman qo'shilmagan edi —
 * repozitoriy ko'p ustunli proyeksiyani {@code Object[]} ichida o'rab
 * qaytargan va uzunlik tekshiruvi jim yiqilgan. Sof funksiya testi buni
 * ushlay olmaydi, chunki unga tayyor raqamlar beriladi.
 */
class TrustScoreServiceIT extends AbstractIntegrationTest {

    @Autowired
    private TrustScoreService trustScoreService;

    private PostFixtures fixtures;

    private PostFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new PostFixtures(jdbcTemplate);
        }
        return fixtures;
    }

    private void insertReview(UUID postId, UUID authorId, UUID subjectId, int rating) {
        jdbcTemplate.update("""
                INSERT INTO reviews (id, post_id, author_id, subject_id, rating)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), postId, authorId, subjectId, rating);
    }

    @Test
    @DisplayName("Baho ballga qo'shiladi (bazadan o'qish to'g'ri ishlaydi)")
    void ratingIsCountedFromDatabase() {
        UUID subject = fixtures().insertUser(860001L, "baholanuvchi", "NONE", 0);
        UUID author = fixtures().insertUser(860002L, "baholovchi", "NONE", 0);
        UUID postId = fixtures().post(subject).status("PUBLISHED").insert();

        insertReview(postId, author, subject, 5);

        int score = trustScoreService.recompute(subject);

        // 10 (bazaviy) + 5 yulduz x 10 = 60
        assertThat(score).isEqualTo(60);
    }

    @Test
    @DisplayName("Bir nechta bahoning o'rtachasi olinadi")
    void averagesSeveralRatings() {
        UUID subject = fixtures().insertUser(860003L, "baholanuvchi2", "NONE", 0);
        UUID first = fixtures().insertUser(860004L, "a", "NONE", 0);
        UUID second = fixtures().insertUser(860005L, "b", "NONE", 0);
        UUID postA = fixtures().post(subject).status("PUBLISHED").insert();
        UUID postB = fixtures().post(subject).status("PUBLISHED").insert();

        insertReview(postA, first, subject, 5);
        insertReview(postB, second, subject, 3);

        int score = trustScoreService.recompute(subject);

        // o'rtacha 4.0 -> 40 ball
        assertThat(score).isEqualTo(50);
    }

    @Test
    @DisplayName("Tasdiqlanish darajasi va asosli shikoyat hisobga olinadi")
    void verificationAndReportsAffectScore() {
        UUID subject = fixtures().insertUser(860006L, "baholanuvchi3", "DOCUMENT", 0);
        UUID reporter = fixtures().insertUser(860007L, "shikoyatchi", "NONE", 0);
        UUID postId = fixtures().post(subject).status("PUBLISHED").insert();

        jdbcTemplate.update("""
                INSERT INTO reports (id, post_id, reported_user_id, reporter_id, reason, status)
                VALUES (?, ?, ?, ?, 'SPAM', 'RESOLVED')
                """, UUID.randomUUID(), postId, subject, reporter);

        int score = trustScoreService.recompute(subject);

        // 10 (bazaviy) + 20 (DOCUMENT) - 10 (asosli shikoyat) = 20
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("Ochiq shikoyat ballni tushirmaydi")
    void openReportDoesNotPunish() {
        UUID subject = fixtures().insertUser(860008L, "baholanuvchi4", "PHONE", 0);
        UUID reporter = fixtures().insertUser(860009L, "shikoyatchi2", "NONE", 0);
        UUID postId = fixtures().post(subject).status("PUBLISHED").insert();

        jdbcTemplate.update("""
                INSERT INTO reports (id, post_id, reported_user_id, reporter_id, reason, status)
                VALUES (?, ?, ?, ?, 'SPAM', 'OPEN')
                """, UUID.randomUUID(), postId, subject, reporter);

        int score = trustScoreService.recompute(subject);

        // 10 + 10 (PHONE), shikoyat hali tekshirilmagan
        assertThat(score).isEqualTo(20);
    }

    @Test
    @DisplayName("Ball bazaga yoziladi")
    void scoreIsPersisted() {
        UUID subject = fixtures().insertUser(860010L, "baholanuvchi5", "PHONE", 0);

        trustScoreService.recompute(subject);

        Integer stored = jdbcTemplate.queryForObject(
                "SELECT trust_score FROM users WHERE id = ?", Integer.class, subject);
        assertThat(stored).isEqualTo(20);
    }
}
