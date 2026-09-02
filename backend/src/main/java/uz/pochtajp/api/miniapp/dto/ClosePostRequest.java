package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.NotNull;
import uz.pochtajp.service.DealService;

/**
 * E'lonni yopish (§6.4, 3-band).
 *
 * <p>Sabab majburiy va uchta variantdan iborat. Bu qulaylik uchun emas:
 * "javob bo'lmadi" ulushi mahsulot muammosini ko'rsatadigan yagona signal,
 * uni "bekor qilindi" bilan qo'shib yuborish ma'lumotni yo'q qiladi.
 *
 * <p>Sessiya va platforma so'ralmaydi: {@code post_close} eventining
 * xossalari (§6.1) — sabab va publish'dan beri o'tgan soat, ular ikkalasi
 * ham serverda bor. Mijozdan olinib ishlatilmaydigan maydon — yolg'on
 * shartnoma.
 *
 * @param reason FOUND — odam topildi | CANCELLED — reja o'zgardi | NO_ANSWER — javob bo'lmadi
 */
public record ClosePostRequest(
        @NotNull(message = "Yopish sababini tanlang.")
        Reason reason
) {

    /** Foydalanuvchiga ko'rinadigan uchta variant (§6.4). */
    public enum Reason {
        FOUND(DealService.Answer.FOUND),
        CANCELLED(DealService.Answer.CANCELLED),
        NO_ANSWER(DealService.Answer.NO_ANSWER);

        private final DealService.Answer answer;

        Reason(DealService.Answer answer) {
            this.answer = answer;
        }

        /** Bot javobi bilan bir xil mantiqqa o'tkazadi — qoidalar bitta joyda. */
        public DealService.Answer toAnswer() {
            return answer;
        }
    }
}
