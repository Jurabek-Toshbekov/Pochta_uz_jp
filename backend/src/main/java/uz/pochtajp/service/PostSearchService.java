package uz.pochtajp.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.PostSearchRequest;
import uz.pochtajp.api.miniapp.dto.PostSearchResponse;
import uz.pochtajp.api.miniapp.dto.PostSort;
import uz.pochtajp.api.miniapp.dto.PostSummaryResponse;
import uz.pochtajp.domain.SearchQuery;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.repository.PostSearchRepository;
import uz.pochtajp.repository.SearchQueryRepository;

/**
 * Qidiruv (§10).
 *
 * <p>Har bir qidiruv <b>ikki joyga</b> yoziladi va bu ataylab:
 * <ul>
 *   <li>{@code events} — voronka va konversiya uchun (JSONB, moslashuvchan)</li>
 *   <li>{@code search_queries} — tipli ustunlar: narx indeksi, talab/taklif
 *       balansi va "top zero-result routes" shu jadvaldan SQL bilan o'qiladi
 *       (§6.3, §11.2). JSONB'dan bunday hisoblash og'ir va noaniq bo'lardi.</li>
 * </ul>
 *
 * <p>Yozish faqat birinchi sahifada — keyingi sahifalar bir xil qidiruvning
 * davomi, ularni sanash zero-result va konversiya foizlarini buzardi.
 */
@Service
public class PostSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostSearchService.class);

    private final PostSearchRepository searchRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final EventLogger eventLogger;

    public PostSearchService(PostSearchRepository searchRepository,
                             SearchQueryRepository searchQueryRepository,
                             EventLogger eventLogger) {
        this.searchRepository = searchRepository;
        this.searchQueryRepository = searchQueryRepository;
        this.eventLogger = eventLogger;
    }

    @Transactional
    public PostSearchResponse search(PostSearchRequest request,
                                     UUID userId,
                                     PostSearchRequest.Context context) {
        long startedAt = System.nanoTime();
        PostSort sort = request.sortOrDefault();
        int size = request.sizeOrDefault();

        // size + 1 — keyingi sahifa bor-yo'qligini bilish uchun.
        List<PostSummaryResponse> rows = searchRepository.search(request, size + 1);
        boolean hasMore = rows.size() > size;
        List<PostSummaryResponse> items = hasMore ? new ArrayList<>(rows.subList(0, size)) : rows;

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            nextCursor = searchRepository.cursorFor(items.get(items.size() - 1), sort).encode();
        }

        Integer totalCount = request.firstPage() ? searchRepository.count(request) : null;
        int latencyMs = (int) ((System.nanoTime() - startedAt) / 1_000_000);

        if (request.firstPage()) {
            int resultCount = totalCount == null ? items.size() : totalCount;
            recordSearch(request, userId, context, resultCount, latencyMs);
        }

        return new PostSearchResponse(items, nextCursor, totalCount, latencyMs);
    }

    private void recordSearch(PostSearchRequest request,
                              UUID userId,
                              PostSearchRequest.Context context,
                              int resultCount,
                              int latencyMs) {
        try {
            SearchQuery entity = new SearchQuery();
            entity.setUserId(userId);
            entity.setSessionId(context == null ? null : context.sessionId());
            entity.setPostType(request.type());
            entity.setDirection(request.direction());
            entity.setOriginAirport(request.primaryOrigin());
            entity.setDestAirport(request.primaryDest());
            entity.setDateFrom(request.dateFrom());
            entity.setDateTo(request.dateTo());
            entity.setCategoryIds(request.categoryIds().isEmpty()
                    ? null
                    : request.categoryIds().toArray(new Short[0]));
            entity.setPriceMax(request.priceMax());
            entity.setPriceCurrency(request.currency());
            entity.setTextQuery(request.textQuery());
            entity.setResultCount(resultCount);
            entity.setLatencyMs(latencyMs);
            searchQueryRepository.save(entity);
        } catch (Exception ex) {
            // Qidiruv natijasi foydalanuvchiga yetib borishi muhimroq (§6.2).
            log.warn("search_queries yozilmadi: {}", ex.getMessage());
        }

        Map<String, Object> filters = filterMap(request);
        eventLogger.track(TrackedEvent.of(EventName.SEARCH_PERFORMED, EventSource.MINIAPP)
                .user(userId)
                .session(context == null ? null : context.sessionId())
                .platform(context == null ? null : context.platform())
                .property("filters", filters)
                .property("result_count", resultCount)
                .property("latency_ms", latencyMs)
                .build());

        if (resultCount == 0) {
            // Qoplanmagan talab — oltin ma'lumot (§6.1). Bu event admin'dagi
            // "Search Insights" sahifasining asosi (§11.2).
            eventLogger.track(TrackedEvent.of(EventName.SEARCH_ZERO_RESULT, EventSource.MINIAPP)
                    .user(userId)
                    .session(context == null ? null : context.sessionId())
                    .platform(context == null ? null : context.platform())
                    .property("filters", filters)
                    .build());
        }
    }

    private Map<String, Object> filterMap(PostSearchRequest request) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (request.type() != null) {
            filters.put("type", request.type().name());
        }
        if (request.direction() != null) {
            filters.put("direction", request.direction().name());
        }
        if (!request.originCodes().isEmpty()) {
            filters.put("origin", List.copyOf(request.originCodes()));
        }
        if (!request.destCodes().isEmpty()) {
            filters.put("dest", List.copyOf(request.destCodes()));
        }
        if (request.dateFrom() != null) {
            filters.put("date_from", request.dateFrom().toString());
        }
        if (request.dateTo() != null) {
            filters.put("date_to", request.dateTo().toString());
        }
        if (!request.categoryIds().isEmpty()) {
            filters.put("categories", List.copyOf(request.categoryIds()));
        }
        if (request.priceMax() != null) {
            filters.put("price_max", request.priceMax());
        }
        if (request.currency() != null) {
            filters.put("currency", request.currency().name());
        }
        if (request.verified()) {
            filters.put("verified_only", true);
        }
        if (request.textQuery() != null) {
            filters.put("has_text_query", true);
        }
        filters.put("sort", request.sortOrDefault().name());
        return filters;
    }
}
