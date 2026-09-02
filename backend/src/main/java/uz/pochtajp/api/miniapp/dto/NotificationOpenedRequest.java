package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Xabarnomadagi havola ochildi (§6.1 — notification_opened).
 * CTR shu chaqiruvdan hisoblanadi.
 */
public record NotificationOpenedRequest(
        @NotNull(message = "E'lon tanlanmagan.")
        UUID postId
) {
}
