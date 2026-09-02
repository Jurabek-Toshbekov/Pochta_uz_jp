package uz.pochtajp.common.exception;

import org.springframework.http.HttpStatus;

/** So'rov chegarasi oshib ketdi (§7.2). 429 + {@code rate_limit_hit} eventi. */
public class RateLimitException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
