package uz.pochtajp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.pochtajp.support.AbstractIntegrationTest;

/**
 * Migratsiya va sxema qoidalari (§1.1, §1.3, §5).
 *
 * <p>Bu test kontekst ko'tarilishining o'ziga tayanadi: {@code ddl-auto=validate}
 * bo'lgani uchun entity va Flyway sxemasi bir-biriga mos kelmasa test hatto
 * boshlanmaydi.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Barcha migratsiyalar muvaffaqiyatli qo'llangan")
    void allMigrationsApplied() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    @DisplayName("Matn qidiruvi tayyor: unaccent, konfiguratsiya, ustun, trigger, indeks (§10.2)")
    void textSearchIsReady() {
        Integer extension = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'unaccent'", Integer.class);
        assertThat(extension).isEqualTo(1);

        Integer config = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_ts_config WHERE cfgname = 'pochta_simple'", Integer.class);
        assertThat(config).isEqualTo(1);

        String columnType = jdbcTemplate.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_name = 'posts' AND column_name = 'search_vector'
                """, String.class);
        assertThat(columnType).isEqualTo("tsvector");

        Integer trigger = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_trigger
                WHERE tgname = 'trg_posts_search_vector' AND NOT tgisinternal
                """, Integer.class);
        assertThat(trigger).isEqualTo(1);

        Integer index = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'posts' AND indexname = 'idx_posts_search_vector'
                """, Integer.class);
        assertThat(index).isEqualTo(1);
    }

    @Test
    @DisplayName("Kontakt ochilishi bir foydalanuvchi uchun bir marta — UNIQUE indeks bor")
    void contactRevealIsUniquePerViewer() {
        Integer index = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'contact_reveals' AND indexname = 'idx_reveal_post_viewer'
                """, Integer.class);
        assertThat(index).isEqualTo(1);
    }

    @Test
    @DisplayName("Reference ma'lumot seed qilingan (V2)")
    void referenceDataSeeded() {
        Integer airports = jdbcTemplate.queryForObject("SELECT count(*) FROM airports", Integer.class);
        Integer categories = jdbcTemplate.queryForObject("SELECT count(*) FROM cargo_categories", Integer.class);
        Integer corridors = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM corridors WHERE code = 'JP_UZ'", Integer.class);

        assertThat(airports).isEqualTo(12);
        assertThat(categories).isEqualTo(7);
        assertThat(corridors).isEqualTo(1);
    }

    @Test
    @DisplayName("HIGH risk kategoriyalarda ogohlantirish matni bor (§7.3)")
    void highRiskCategoriesHaveWarnings() {
        Integer missing = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM cargo_categories
                WHERE risk_level = 'HIGH' AND (warning_uz IS NULL OR btrim(warning_uz) = '')
                """, Integer.class);

        assertThat(missing).isZero();
    }

    @Test
    @DisplayName("Eski ma'lumot uchun posts_legacy jadvali saqlangan (§5.3)")
    void legacyTableKept() {
        Integer exists = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'posts_legacy'
                """, Integer.class);
        assertThat(exists).isEqualTo(1);

        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'posts_legacy' AND column_name IN
                    ('migration_status','migration_note','migrated_post_id','migrated_at')
                ORDER BY column_name
                """, String.class);
        assertThat(columns).containsExactly("migrated_at", "migrated_post_id", "migration_note", "migration_status");
    }

    @Test
    @DisplayName("Har bir asosiy jadvalda deleted_at bor — soft delete (§1.1, §5.1)")
    void mainTablesSupportSoftDelete() {
        List<String> tables = List.of("users", "posts", "notification_subscriptions", "reviews");

        for (String table : tables) {
            Integer hasColumn = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = ? AND column_name = 'deleted_at'
                    """, Integer.class, table);
            assertThat(hasColumn).as("%s.deleted_at", table).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Pul ustunlari NUMERIC — double ishlatilmagan (§5.1)")
    void moneyColumnsAreNumeric() {
        String type = jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'posts' AND column_name = 'price_amount'
                """, String.class);

        assertThat(type).isEqualTo("numeric");
    }

    @Test
    @DisplayName("Sana ustunlari DATE — string emas (§1.5)")
    void dateColumnsAreTyped() {
        List<String> types = jdbcTemplate.queryForList("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'posts' AND column_name IN ('depart_date','deadline_date')
                """, String.class);

        assertThat(types).containsOnly("date");
    }
}
