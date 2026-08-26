package uz.pochtajp.api.miniapp.dto;

import java.util.List;

/**
 * Keyset pagination javobi (§10.2) — offset yo'q.
 *
 * @param items       shu sahifadagi e'lonlar
 * @param nextCursor  keyingi sahifa uchun kursor; {@code null} bo'lsa oxiri
 * @param totalCount  faqat birinchi sahifada hisoblanadi; keyingi sahifalarda {@code null}
 * @param latencyMs   server tomondagi qidiruv vaqti — {@code search_queries}ga ham yoziladi
 */
public record PostSearchResponse(
        List<PostSummaryResponse> items,
        String nextCursor,
        Integer totalCount,
        int latencyMs
) {

    public boolean isEmptyResult() {
        return items.isEmpty();
    }
}
