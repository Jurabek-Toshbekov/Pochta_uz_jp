package uz.pochtajp.common.exception;

import org.springframework.http.HttpStatus;

/** Resurs topilmadi (yoki soft delete qilingan — §1.1). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
