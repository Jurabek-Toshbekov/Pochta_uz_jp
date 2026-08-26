package uz.pochtajp.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API xato javobining yagona formati. Mini App shu formatga tayanadi.
 *
 * <p>Matn qoidasi (§9.4): xato kechirim so'ramaydi, nima bo'lganini va nima
 * qilish kerakligini aytadi. Shuning uchun {@code code} — mashina uchun,
 * {@code message} — foydalanuvchi uchun.
 *
 * @param code       mashina o'qiy oladigan kod: VALIDATION_FAILED, UNAUTHORIZED, ...
 * @param message    foydalanuvchiga ko'rsatiladigan matn (o'zbekcha)
 * @param fieldErrors maydon nomi -> xato matni (validatsiya xatolarida)
 * @param occurredAt xato vaqti (UTC)
 */
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors,
        List<String> details,
        Instant occurredAt
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null, null, Instant.now());
    }

    public static ApiErrorResponse validation(String message, Map<String, String> fieldErrors) {
        return new ApiErrorResponse("VALIDATION_FAILED", message, fieldErrors, null, Instant.now());
    }
}
