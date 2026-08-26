package uz.pochtajp.api.miniapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Event batch'i. Mini App 10 event yoki 5 soniyada bir yuboradi (§6.2).
 *
 * @param events bir so'rovda maksimal 100 event
 */
public record TrackEventBatchRequest(
        @NotEmpty(message = "Event ro'yxati bo'sh")
        @Size(max = 100, message = "Bir so'rovda 100 tadan ko'p event yuborilmaydi")
        @Valid
        List<TrackEventRequest> events
) {
}
