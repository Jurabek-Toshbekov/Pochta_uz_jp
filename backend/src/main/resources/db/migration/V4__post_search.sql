-- =====================================================================
-- V4__post_search.sql — matn qidiruvi (CLAUDE.md §10.2)
--
-- Talab: "Matn qidiruvi: `comment` ustida `tsvector` (`simple` konfiguratsiyasi
-- + `unaccent`), alohida ustunda saqlanadi, trigger bilan yangilanadi."
--
-- Nima uchun `simple` + `unaccent`:
--   * o'zbek tili uchun Postgres'da stemmer yo'q — `english` stemmer o'zbek
--     so'zlarini buzadi, shuning uchun `simple` (faqat lowercase + stopword yo'q)
--   * `unaccent` diakritik belgilarni normallashtiradi (ў -> у, й -> и)
--   * tutuq belgisi (o', g') esa alohida hal qilinadi — pastdagi
--     `pochta_normalize` funksiyasiga qarang
--
-- MUHIM: `CREATE EXTENSION` uchun bir marta superuser huquqi kerak. Agar DB
-- foydalanuvchisi superuser bo'lmasa, extension'ni oldindan yaratib qo'yish
-- kerak (README §2 da yozilgan).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS unaccent;

-- ---------------------------------------------------------------------
-- Apostrof normalizatsiyasi.
--
-- `unaccent` diakritik belgilarni olib tashlaydi, lekin o'zbek lotinidagi
-- tutuq belgisini (o', g', ' , ' , ʻ) YO'Q qilmaydi — u harf emas, tinish
-- belgisi. Natijada "Farg'ona" tsvector'da "farg" + "ona" bo'lib ajralib
-- ketadi va "Fargona" deb qidirgan odam topmaydi.
--
-- Shu sabab matn indeksga tushishdan oldin tutuq belgilaridan tozalanadi.
-- Ayni funksiya qidiruv so'roviga ham qo'llaniladi — ikki tomon bir xil
-- normallashtirilmasa qidiruv ishlamaydi.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pochta_normalize(input text) RETURNS text AS $$
    SELECT translate(coalesce(input, ''), '''‘’`´ʻʼ', '');
$$ LANGUAGE sql IMMUTABLE;

COMMENT ON FUNCTION pochta_normalize(text) IS
    'Tutuq belgilarini olib tashlaydi. Indeks va qidiruv so''rovi uchun bir xil (V4, §10.2).';

-- `simple` konfiguratsiyasi ustiga unaccent qo'shamiz.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'pochta_simple') THEN
        CREATE TEXT SEARCH CONFIGURATION pochta_simple (COPY = simple);
        ALTER TEXT SEARCH CONFIGURATION pochta_simple
            ALTER MAPPING FOR hword, hword_part, word WITH unaccent, simple;
    END IF;
END
$$;

-- ---------------------------------------------------------------------
-- Qidiruv ustuni. Entity'da mapping qilinmaydi — uni faqat trigger to'ldiradi,
-- Hibernate hech qachon yozmaydi (ddl-auto=validate ortiqcha ustundan
-- shikoyat qilmaydi).
-- ---------------------------------------------------------------------
ALTER TABLE posts ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE OR REPLACE FUNCTION posts_search_vector_update() RETURNS trigger AS $$
BEGIN
    -- Izohdan tashqari erkin kiritilgan joy nomlari ham qidiruvga tushadi:
    -- "Yokohama" yoki "Samarqand" deb yozgan odam topilishi kerak.
    NEW.search_vector := to_tsvector(
        'public.pochta_simple'::regconfig,
        pochta_normalize(
            coalesce(NEW.comment, '') || ' ' ||
            coalesce(NEW.final_destination, '') || ' ' ||
            coalesce(NEW.origin_city_free, '') || ' ' ||
            coalesce(NEW.dest_city_free, '')
        )
    );
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION posts_search_vector_update() IS
    'posts.search_vector ni to''ldiradi. Faqat trigger chaqiradi (V4, §10.2).';

DROP TRIGGER IF EXISTS trg_posts_search_vector ON posts;
CREATE TRIGGER trg_posts_search_vector
    BEFORE INSERT OR UPDATE OF comment, final_destination, origin_city_free, dest_city_free
    ON posts
    FOR EACH ROW
    EXECUTE FUNCTION posts_search_vector_update();

-- Mavjud qatorlarni to'ldiramiz (V3 dan ko'chgan e'lonlar ham bor).
UPDATE posts SET search_vector = to_tsvector(
        'public.pochta_simple'::regconfig,
        pochta_normalize(
            coalesce(comment, '') || ' ' ||
            coalesce(final_destination, '') || ' ' ||
            coalesce(origin_city_free, '') || ' ' ||
            coalesce(dest_city_free, '')
        )
    )
WHERE search_vector IS NULL;

CREATE INDEX IF NOT EXISTS idx_posts_search_vector ON posts USING GIN (search_vector);

-- ---------------------------------------------------------------------
-- Qidiruv uchun qo'shimcha indekslar (§10.2 — keyset pagination).
--
-- Asosiy tartib `published_at DESC, id DESC` bo'yicha keyset ishlaydi;
-- V1 dagi idx_posts_pub faqat published_at ustida edi, id qo'shilishi
-- keyset so'rovini indeksdan to'liq o'qishga imkon beradi.
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_posts_feed
    ON posts (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- "Uchish sanasi bo'yicha" tartibi.
CREATE INDEX IF NOT EXISTS idx_posts_feed_date
    ON posts (coalesce(depart_date, deadline_date) ASC, id ASC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- Muddati o'tgan e'lonlarni chiqarib tashlash uchun.
CREATE INDEX IF NOT EXISTS idx_posts_expires
    ON posts (expires_at)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Kontakt ochilishi bir foydalanuvchi uchun bir marta hisoblanadi:
-- fill rate va match latency metrikalari (§6.3) takroriy bosishlardan
-- shishib ketmasligi kerak.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS idx_reveal_post_viewer
    ON contact_reveals (post_id, viewer_id);
