package uz.pochtajp.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import uz.pochtajp.common.exception.ApiException;
import uz.pochtajp.common.exception.RateLimitException;
import uz.pochtajp.common.exception.ValidationException;

/**
 * Yagona xato ishlovchi. Ikki qoida:
 * <ul>
 *   <li>{@code e.printStackTrace()} ishlatilmaydi — faqat SLF4J (§1.8)</li>
 *   <li>log'da PII bo'lmaydi: telefon, ism, initData yozilmaydi (§1.7)</li>
 * </ul>
 *
 * <p>Kutilmagan xatoning tafsiloti foydalanuvchiga ketmaydi — faqat log'da qoladi.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Biznes qoidasi buzilgan — maydon nomlari bilan qaytadi. */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessValidation(ValidationException ex,
                                                                     HttpServletRequest request) {
        log.warn("Biznes validatsiyasi: path={} fields={}", request.getRequestURI(), ex.getFieldErrors().keySet());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.validation(ex.getMessage(), ex.getFieldErrors()));
    }

    /** Biznes xatolari — status va matn o'zida keladi. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        log.warn("API xato: code={} status={} path={}", ex.getCode(), ex.getStatus().value(), request.getRequestURI());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex instanceof RateLimitException rateLimit) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.getRetryAfterSeconds()));
        }
        return builder.body(ApiErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    /** {@code @Valid} DTO xatolari — maydon nomi bilan qaytadi. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        log.warn("Validatsiya xatosi: path={} fields={}", request.getRequestURI(), fields.keySet());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.validation("Ma'lumot to'liq emas. Belgilangan maydonlarni tekshiring.", fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        log.warn("Parametr validatsiyasi xatosi: path={}", request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_FAILED", "So'rov parametrlari to'g'ri emas."));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        // Xabar tanasi log'ga yozilmaydi — ichida PII bo'lishi mumkin (§1.7).
        log.warn("So'rovni o'qib bo'lmadi: path={} type={}", request.getRequestURI(), ex.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", "So'rov formati to'g'ri emas."));
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        log.debug("Marshrut topilmadi: path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", "So'ralgan manzil topilmadi."));
    }

    /** Oxirgi to'siq. Tafsilot faqat log'da qoladi. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Kutilmagan xato: path={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR",
                        "Xatolik yuz berdi. Birozdan keyin qayta urinib ko'ring."));
    }
}
