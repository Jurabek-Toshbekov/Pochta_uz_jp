package uz.pochtajp.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Biznes qoidasi buzilgan (Bean Validation qo'lidan kelmaydigan holatlar):
 * "CARRY e'londa uchish sanasi bo'lishi shart", "sana o'tib ketgan" va shu kabilar.
 *
 * <p>Xato matni maydon nomiga bog'lanadi — Mini App aynan o'sha inputni belgilaydi (§9.4).
 */
public class ValidationException extends ApiException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Map.of());
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    /** Bitta maydon uchun qisqa yo'l. */
    public static ValidationException field(String field, String message) {
        return new ValidationException(message, Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /** Bir nechta xatoni yig'ish uchun. */
    public static final class Collector {

        private final Map<String, String> errors = new LinkedHashMap<>();

        public Collector add(String field, String message) {
            errors.putIfAbsent(field, message);
            return this;
        }

        public boolean isEmpty() {
            return errors.isEmpty();
        }

        public void throwIfAny() {
            if (!errors.isEmpty()) {
                throw new ValidationException("Ma'lumotni tekshirib chiqing.", errors);
            }
        }
    }
}
