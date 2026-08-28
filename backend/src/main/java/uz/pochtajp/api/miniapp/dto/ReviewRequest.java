package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Bitim sherigiga baho (§6.4, 7-band). */
public record ReviewRequest(
        @NotNull(message = "E'lon tanlanmagan.")
        UUID postId,

        @Min(value = 1, message = "Baho 1 dan 5 gacha.")
        @Max(value = 5, message = "Baho 1 dan 5 gacha.")
        int rating,

        @Size(max = 1000, message = "Izoh 1000 belgidan oshmasin.")
        String comment
) {
}
