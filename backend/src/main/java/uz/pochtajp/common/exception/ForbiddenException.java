package uz.pochtajp.common.exception;

import org.springframework.http.HttpStatus;

/** Autentifikatsiya bor, lekin huquq yo'q (masalan e'lon egasi emas, yoki BLOCKED). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
