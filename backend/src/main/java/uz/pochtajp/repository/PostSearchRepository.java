package uz.pochtajp.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uz.pochtajp.api.miniapp.dto.PostSearchRequest;
import uz.pochtajp.api.miniapp.dto.PostSort;
import uz.pochtajp.api.miniapp.dto.PostSummaryResponse;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;
import uz.pochtajp.domain.enums.VerificationLevel;
import uz.pochtajp.service.SearchCursor;

/**
 * Qidiruv so'rovi (§10.2). Ataylab native SQL:
 * <ul>
 *   <li>dinamik filtrlar Criteria API'da o'qilmas bo'lib ketadi</li>
 *   <li>keyset taqqoslash ({@code (key, id) < (?, ?)}) JPQL'da yo'q</li>
 *   <li>{@code tsvector @@ websearch_to_tsquery} — Postgres'ga xos</li>
 * </ul>
 *
 * <p>Faqat ko'rinishi kerak bo'lgan e'lonlar: {@code PUBLISHED}, o'chirilmagan,
 * muddati o'tmagan. Kontakt ustunlari SELECT'ga umuman kirmaydi (§6.4, 2-band).
 */
@Repository
public class PostSearchRepository {

    private static final String SELECT_COLUMNS = """
            SELECT p.id, p.post_type, p.direction,
                   p.origin_airport, p.dest_airport, p.origin_city_free, p.dest_city_free,
                   p.final_destination, p.depart_date, p.deadline_date, p.date_flexible_days,
                   p.weight_kg, p.weight_kg_max,
                   p.price_amount, p.price_currency, p.price_unit,
                   p.comment, p.view_count, p.contact_reveal_count,
                   p.published_at, p.expires_at,
                   u.verification_level, u.trust_score
            """;

    private static final String FROM_AND_BASE = """
            FROM posts p
            JOIN users u ON u.id = p.user_id
            WHERE p.deleted_at IS NULL
              AND p.status = 'PUBLISHED'
              AND (p.expires_at IS NULL OR p.expires_at > now())
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PostSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param limit qancha qator kerak (chaqiruvchi {@code size + 1} beradi — keyingi
     *              sahifa bor-yo'qligini bilish uchun)
     */
    public List<PostSummaryResponse> search(PostSearchRequest request, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(FROM_AND_BASE);

        appendFilters(request, sql, params);
        appendKeyset(request, sql, params);

        PostSort sort = request.sortOrDefault();
        sql.append(" ORDER BY ").append(sort.keyExpression()).append(' ').append(sort.direction())
                .append(", p.id ").append(sort.direction())
                .append(" LIMIT :limit");
        params.addValue("limit", limit);

        List<PostSummaryResponse> rows = jdbc.query(sql.toString(), params, PostSearchRepository::mapRow);
        return attachCategories(rows);
    }

    /**
     * Bitta e'lonning ochiq ko'rinishi (kontaktsiz). Qidiruv bilan bir xil
     * shartlar: faqat PUBLISHED, o'chirilmagan, muddati o'tmagan.
     */
    public Optional<PostSummaryResponse> findPublicById(UUID postId) {
        String sql = SELECT_COLUMNS + FROM_AND_BASE + " AND p.id = :postId";
        List<PostSummaryResponse> rows = jdbc.query(sql,
                new MapSqlParameterSource("postId", postId), PostSearchRepository::mapRow);
        return attachCategories(rows).stream().findFirst();
    }

    /** Birinchi sahifada umumiy son — "N natija topildi" va analitika uchun. */
    public int count(PostSearchRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT count(*) ").append(FROM_AND_BASE);
        appendFilters(request, sql, params);

        Integer count = jdbc.queryForObject(sql.toString(), params, Integer.class);
        return count == null ? 0 : count;
    }

    /** Kursor uchun oxirgi qatorning saralash qiymati. */
    public SearchCursor cursorFor(PostSummaryResponse row, PostSort sort) {
        String key = switch (sort) {
            case NEWEST -> row.publishedAt() == null ? "" : row.publishedAt().toString();
            case DEPART_DATE -> {
                LocalDate date = row.departDate() != null ? row.departDate() : row.deadlineDate();
                yield date == null ? "" : date.toString();
            }
            case CHEAPEST -> row.priceAmount() == null
                    ? "999999999999"
                    : row.priceAmount().toPlainString();
            case RATING -> String.valueOf(row.trustScore());
        };
        return new SearchCursor(key, row.id());
    }

    // ------------------------------------------------------------------

    private void appendFilters(PostSearchRequest request, StringBuilder sql, MapSqlParameterSource params) {
        if (request.type() != null) {
            sql.append(" AND p.post_type = :type");
            params.addValue("type", request.type().name());
        }
        if (request.direction() != null) {
            sql.append(" AND p.direction = :direction");
            params.addValue("direction", request.direction().name());
        }
        if (!request.originCodes().isEmpty()) {
            sql.append(" AND p.origin_airport IN (:origins)");
            params.addValue("origins", request.originCodes());
        }
        if (!request.destCodes().isEmpty()) {
            sql.append(" AND p.dest_airport IN (:dests)");
            params.addValue("dests", request.destCodes());
        }
        if (request.dateFrom() != null) {
            sql.append(" AND coalesce(p.depart_date, p.deadline_date) >= :dateFrom");
            params.addValue("dateFrom", request.dateFrom());
        }
        if (request.dateTo() != null) {
            sql.append(" AND coalesce(p.depart_date, p.deadline_date) <= :dateTo");
            params.addValue("dateTo", request.dateTo());
        }
        if (!request.categoryIds().isEmpty()) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM post_categories pc
                                 WHERE pc.post_id = p.id AND pc.category_id IN (:categories))
                    """);
            params.addValue("categories", request.categoryIds());
        }
        if (request.priceMax() != null) {
            // "Kelishamiz" e'lonlari narx filtridan chiqib ketmasligi kerak:
            // ular narxi belgilanmagani uchun har qanday budjetga mos keladi.
            sql.append(" AND (p.price_amount IS NULL OR p.price_amount <= :priceMax)");
            params.addValue("priceMax", request.priceMax());
        }
        if (request.currency() != null) {
            sql.append(" AND (p.price_currency IS NULL OR p.price_currency = :currency)");
            params.addValue("currency", request.currency().name());
        }
        if (request.verified()) {
            sql.append(" AND u.verification_level <> 'NONE'");
        }
        if (request.textQuery() != null) {
            // websearch_to_tsquery foydalanuvchi kiritgan matnni xatosiz qabul
            // qiladi: qo'shtirnoq, OR, - belgilarini tushunadi va buzilmaydi.
            // pochta_normalize — indeksdagi matn bilan bir xil normalizatsiya (V4).
            sql.append(" AND p.search_vector @@ websearch_to_tsquery("
                    + "'public.pochta_simple', pochta_normalize(:textQuery))");
            params.addValue("textQuery", request.textQuery());
        }
    }

    private void appendKeyset(PostSearchRequest request, StringBuilder sql, MapSqlParameterSource params) {
        Optional<SearchCursor> cursor = SearchCursor.decode(request.cursor());
        if (cursor.isEmpty()) {
            return;
        }
        PostSort sort = request.sortOrDefault();
        String key = sort.keyExpression();
        String cast = "CAST(:cursorKey AS " + sort.keyType() + ")";

        // (key, id) juftligi bo'yicha "shu qatordan keyingisi".
        sql.append(" AND (").append(key).append(' ').append(sort.comparison()).append(' ').append(cast)
                .append(" OR (").append(key).append(" = ").append(cast)
                .append(" AND p.id ").append(sort.comparison()).append(" :cursorId))");

        params.addValue("cursorKey", cursor.get().key());
        params.addValue("cursorId", cursor.get().id());
    }

    /**
     * Kategoriyalar alohida so'rov bilan olinadi — JOIN qilinsa qatorlar
     * ko'payib keyset va LIMIT buziladi (klassik N+1'ning teskarisi).
     */
    private List<PostSummaryResponse> attachCategories(List<PostSummaryResponse> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        List<UUID> ids = rows.stream().map(PostSummaryResponse::id).toList();
        Map<UUID, List<Short>> byPost = new HashMap<>();
        jdbc.query("SELECT post_id, category_id FROM post_categories WHERE post_id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    UUID postId = rs.getObject("post_id", UUID.class);
                    byPost.computeIfAbsent(postId, key -> new ArrayList<>()).add(rs.getShort("category_id"));
                });

        List<PostSummaryResponse> result = new ArrayList<>(rows.size());
        for (PostSummaryResponse row : rows) {
            List<Short> categoryIds = byPost.getOrDefault(row.id(), new ArrayList<>());
            categoryIds.sort(Short::compareTo);
            result.add(withCategories(row, List.copyOf(categoryIds)));
        }
        return result;
    }

    private static PostSummaryResponse withCategories(PostSummaryResponse row, List<Short> categoryIds) {
        return new PostSummaryResponse(
                row.id(), row.postType(), row.direction(),
                row.originAirport(), row.destAirport(), row.originCityFree(), row.destCityFree(),
                row.finalDestination(), row.departDate(), row.deadlineDate(), row.dateFlexibleDays(),
                row.weightKg(), row.weightKgMax(),
                row.priceAmount(), row.priceCurrency(), row.priceUnit(),
                categoryIds, row.comment(), row.verified(), row.verificationLevel(), row.trustScore(),
                row.viewCount(), row.contactRevealCount(), row.publishedAt(), row.expiresAt());
    }

    private static PostSummaryResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        VerificationLevel verification = VerificationLevel.valueOf(rs.getString("verification_level"));
        return new PostSummaryResponse(
                rs.getObject("id", UUID.class),
                PostType.valueOf(rs.getString("post_type")),
                Direction.valueOf(rs.getString("direction")),
                rs.getString("origin_airport"),
                rs.getString("dest_airport"),
                rs.getString("origin_city_free"),
                rs.getString("dest_city_free"),
                rs.getString("final_destination"),
                localDate(rs, "depart_date"),
                localDate(rs, "deadline_date"),
                rs.getInt("date_flexible_days"),
                rs.getBigDecimal("weight_kg"),
                rs.getBigDecimal("weight_kg_max"),
                rs.getBigDecimal("price_amount"),
                enumOrNull(rs.getString("price_currency"), Currency.class),
                enumOrNull(rs.getString("price_unit"), PriceUnit.class),
                List.of(),
                rs.getString("comment"),
                verification != VerificationLevel.NONE,
                verification,
                rs.getInt("trust_score"),
                rs.getInt("view_count"),
                rs.getInt("contact_reveal_count"),
                instant(rs, "published_at"),
                instant(rs, "expires_at"));
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

}
