package uz.pochtajp.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.domain.enums.VerificationLevel;
import uz.pochtajp.service.TrustScoreService.Inputs;

/**
 * Ishonch balli formulasi (§13, 5-bosqich).
 *
 * <p>Formula ochiq bo'lishi kerak: foydalanuvchi "nega ballim past" deb
 * so'rasa javob bo'lsin. Test — o'sha javobning yozma shakli.
 */
class TrustScoreServiceTest {

    @Test
    @DisplayName("Yangi foydalanuvchi nolga tushmaydi — u yomon emas, noma'lum")
    void newUserGetsNeutralBase() {
        int score = TrustScoreService.calculate(
                new Inputs(0, 0, 0, 0, VerificationLevel.NONE));

        assertThat(score).isEqualTo(TrustScoreService.NEUTRAL_BASE);
    }

    @Test
    @DisplayName("Besh yulduzli baholar 50 ball qo'shadi")
    void perfectRatingAddsFifty() {
        int score = TrustScoreService.calculate(
                new Inputs(5.0, 4, 0, 0, VerificationLevel.NONE));

        assertThat(score).isEqualTo(TrustScoreService.NEUTRAL_BASE + 50);
    }

    @Test
    @DisplayName("Bahosi yo'q bo'lsa baho qismi qo'shilmaydi")
    void ratingIgnoredWithoutReviews() {
        int score = TrustScoreService.calculate(
                new Inputs(5.0, 0, 0, 0, VerificationLevel.NONE));

        assertThat(score).isEqualTo(TrustScoreService.NEUTRAL_BASE);
    }

    @Test
    @DisplayName("Yakunlangan bitimlar 20 ballgacha qo'shadi")
    void confirmedDealsAreCapped() {
        int few = TrustScoreService.calculate(new Inputs(0, 0, 2, 0, VerificationLevel.NONE));
        int many = TrustScoreService.calculate(new Inputs(0, 0, 50, 0, VerificationLevel.NONE));

        assertThat(few).isEqualTo(TrustScoreService.NEUTRAL_BASE + 10);
        assertThat(many).isEqualTo(TrustScoreService.NEUTRAL_BASE + 20);
    }

    @Test
    @DisplayName("Tasdiqlanish darajasi ball qo'shadi")
    void verificationAddsPoints() {
        int phone = TrustScoreService.calculate(new Inputs(0, 0, 0, 0, VerificationLevel.PHONE));
        int document = TrustScoreService.calculate(new Inputs(0, 0, 0, 0, VerificationLevel.DOCUMENT));

        assertThat(phone).isEqualTo(TrustScoreService.NEUTRAL_BASE + 10);
        assertThat(document).isEqualTo(TrustScoreService.NEUTRAL_BASE + 20);
    }

    @Test
    @DisplayName("Asosli shikoyat ballni tushiradi, lekin 40 dan ortiq emas")
    void reportsSubtractWithCap() {
        int one = TrustScoreService.calculate(new Inputs(5.0, 10, 0, 1, VerificationLevel.NONE));
        int many = TrustScoreService.calculate(new Inputs(5.0, 10, 0, 99, VerificationLevel.NONE));

        assertThat(one).isEqualTo(TrustScoreService.NEUTRAL_BASE + 50 - 10);
        assertThat(many).isEqualTo(TrustScoreService.NEUTRAL_BASE + 50 - 40);
    }

    @Test
    @DisplayName("Natija hech qachon 0 dan past va 100 dan yuqori bo'lmaydi")
    void resultStaysInRange() {
        int lowest = TrustScoreService.calculate(new Inputs(0, 0, 0, 99, VerificationLevel.NONE));
        int highest = TrustScoreService.calculate(
                new Inputs(5.0, 100, 100, 0, VerificationLevel.DOCUMENT));

        assertThat(lowest).isZero();
        assertThat(highest).isEqualTo(TrustScoreService.MAX_SCORE);
    }

    @Test
    @DisplayName("null tasdiqlanish darajasi NONE deb qaraladi")
    void nullVerificationIsTreatedAsNone() {
        int score = TrustScoreService.calculate(new Inputs(0, 0, 0, 0, null));

        assertThat(score).isEqualTo(TrustScoreService.NEUTRAL_BASE);
    }
}
