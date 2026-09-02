package uz.pochtajp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Kim ekanligi aniqlanmadi: token yo'q, yaroqsiz yoki muddati tugagan.
 *
 * <p>{@link ForbiddenException} dan farqi muhim: 401 — "qaytadan kiring",
 * 403 — "kirdingiz, lekin bunga huquqingiz yo'q". Klient bu ikkisiga
 * boshqacha javob beradi (birida token yangilanadi, ikkinchisida yangilash
 * foydasiz). Mini App tomonida initData uchun allaqachon 401 qaytariladi —
 * admin API ham shu qoidada bo'lishi kerak.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
