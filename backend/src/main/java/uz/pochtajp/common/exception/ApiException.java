package uz.pochtajp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Biznes xatolarining asosi. Har biri HTTP statusi, mashina kodi va
 * foydalanuvchiga ko'rsatiladigan o'zbekcha matn bilan keladi.
 *
 * <p>PII (telefon, ism) xato matniga qo'shilmaydi (§1.7).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
