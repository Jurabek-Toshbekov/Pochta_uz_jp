package uz.pochtajp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Telegram {@code initData} imzosi yoki muddati yaroqsiz (§7.1).
 * Sabab faqat log'ga yoziladi, foydalanuvchiga umumiy matn ketadi.
 */
public class InitDataInvalidException extends ApiException {

    public InitDataInvalidException(String reason) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", reason);
    }
}
