package uz.pochtajp.support;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Qidiruv testlari uchun ma'lumot. Ataylab to'g'ridan-to'g'ri SQL:
 * {@code published_at}, {@code trust_score} va {@code expires_at} kabi
 * maydonlarni API orqali qo'yib bo'lmaydi, tartib va filtrlarni tekshirish
 * uchun esa aynan ular kerak.
 *
 * <p>{@code search_vector} qo'lda yozilmaydi — uni V4 trigger'i to'ldiradi,
 * ya'ni testlar trigger'ning o'zini ham tekshiradi.
 */
public final class PostFixtures {

    private final JdbcTemplate jdbc;

    public PostFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertUser(long telegramId, String username, String verificationLevel, int trustScore) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, telegram_id, username, first_name, verification_level, trust_score)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, telegramId, username, "Test", verificationLevel, trustScore);
        return id;
    }

    public Builder post(UUID userId) {
        return new Builder(jdbc, userId);
    }

    /** E'lon qurish. Standart holat: CARRY, JP_UZ, NRT→TAS, ertaga, PUBLISHED. */
    public static final class Builder {

        private final JdbcTemplate jdbc;
        private final UUID userId;

        private String postType = "CARRY";
        private String direction = "JP_UZ";
        private String originAirport = "NRT";
        private String destAirport = "TAS";
        private String originCityFree;
        private String destCityFree;
        private String finalDestination;
        private LocalDate departDate = LocalDate.now().plusDays(7);
        private LocalDate deadlineDate;
        private short flexibleDays = 0;
        private BigDecimal weightKg = new BigDecimal("10.00");
        private BigDecimal priceAmount = new BigDecimal("2000.00");
        private String priceCurrency = "JPY";
        private String priceUnit = "PER_KG";
        private String comment;
        private String status = "PUBLISHED";
        private Instant publishedAt = Instant.now();
        private Instant expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        private List<Short> categoryIds = List.of((short) 1);

        private Builder(JdbcTemplate jdbc, UUID userId) {
            this.jdbc = jdbc;
            this.userId = userId;
        }

        public Builder type(String value) {
            this.postType = value;
            return this;
        }

        public Builder direction(String value) {
            this.direction = value;
            return this;
        }

        public Builder route(String origin, String dest) {
            this.originAirport = origin;
            this.destAirport = dest;
            return this;
        }

        public Builder freeRoute(String originCity, String destCity) {
            this.originAirport = null;
            this.destAirport = null;
            this.originCityFree = originCity;
            this.destCityFree = destCity;
            return this;
        }

        public Builder finalDestination(String value) {
            this.finalDestination = value;
            return this;
        }

        public Builder departDate(LocalDate value) {
            this.departDate = value;
            this.deadlineDate = null;
            return this;
        }

        public Builder deadlineDate(LocalDate value) {
            this.deadlineDate = value;
            this.departDate = null;
            return this;
        }

        public Builder flexibleDays(int value) {
            this.flexibleDays = (short) value;
            return this;
        }

        public Builder price(String amount, String currency, String unit) {
            this.priceAmount = amount == null ? null : new BigDecimal(amount);
            this.priceCurrency = amount == null ? null : currency;
            this.priceUnit = unit;
            return this;
        }

        public Builder negotiable() {
            return price(null, null, "NEGOTIABLE");
        }

        public Builder comment(String value) {
            this.comment = value;
            return this;
        }

        public Builder status(String value) {
            this.status = value;
            return this;
        }

        public Builder publishedAt(Instant value) {
            this.publishedAt = value;
            return this;
        }

        public Builder expiresAt(Instant value) {
            this.expiresAt = value;
            return this;
        }

        public Builder categories(Short... ids) {
            this.categoryIds = List.of(ids);
            return this;
        }

        public UUID insert() {
            UUID id = UUID.randomUUID();
            Short corridorId = jdbc.queryForObject(
                    "SELECT id FROM corridors WHERE code = 'JP_UZ'", Short.class);

            jdbc.update("""
                    INSERT INTO posts (
                        id, user_id, corridor_id, post_type, direction,
                        origin_airport, dest_airport, origin_city_free, dest_city_free, final_destination,
                        depart_date, deadline_date, date_flexible_days,
                        weight_kg, price_amount, price_currency, price_unit,
                        comment, contact_telegram, safety_checklist_ok,
                        status, published_at, expires_at, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, 'MINIAPP')
                    """,
                    id, userId, corridorId, postType, direction,
                    originAirport, destAirport, originCityFree, destCityFree, finalDestination,
                    departDate, deadlineDate, flexibleDays,
                    weightKg, priceAmount, priceCurrency, priceUnit,
                    comment, "owner_" + userId.toString().substring(0, 8),
                    status,
                    publishedAt == null ? null : Timestamp.from(publishedAt),
                    expiresAt == null ? null : Timestamp.from(expiresAt));

            for (Short categoryId : categoryIds) {
                jdbc.update("INSERT INTO post_categories (post_id, category_id) VALUES (?, ?)",
                        id, categoryId);
            }
            return id;
        }
    }
}
