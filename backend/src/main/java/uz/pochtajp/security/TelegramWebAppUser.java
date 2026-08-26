package uz.pochtajp.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code initData} ichidagi {@code user} JSON obyekti (§7.1).
 * Faqat kerakli maydonlar olinadi, qolganlari e'tiborsiz qoldiriladi.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramWebAppUser(
        @JsonProperty("id") Long id,
        @JsonProperty("username") String username,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("language_code") String languageCode,
        @JsonProperty("is_premium") Boolean isPremium,
        @JsonProperty("allows_write_to_pm") Boolean allowsWriteToPm
) {
}
