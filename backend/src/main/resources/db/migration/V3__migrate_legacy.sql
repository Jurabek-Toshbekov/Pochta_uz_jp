-- =====================================================================
-- V3__migrate_legacy.sql — eski `post` jadvalini saqlash va ko'chirish
-- (CLAUDE.md §5.3)
--
-- MUTLAQ QOIDA (§1.1): eski ma'lumot HECH QACHON o'chirilmaydi.
-- Shu sababli:
--   1) eski `post` jadvali `posts_legacy` nomiga o'zgartiriladi (DROP yo'q)
--   2) best-effort parser bilan yangi sxemaga ko'chiriladi
--   3) ko'chirilmagan qatorlar `posts_legacy`da sababi bilan qoladi
--
-- Eski sxema butunlay `String`da edi (§2, 9-nuqson): sana, narx, yo'nalish —
-- hammasi erkin matn. Shuning uchun `depart_date` va `price_amount` kabi
-- tipli maydonlar NULL qoldiriladi (§1.5 — erkin matnni tipli ustunga
-- tiqish taqiqlangan), asl matn esa `comment`da to'liq saqlanadi.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1-qadam: `post` -> `posts_legacy` (agar mavjud bo'lsa)
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'post')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name = 'posts_legacy')
    THEN
        EXECUTE 'ALTER TABLE post RENAME TO posts_legacy';
    END IF;
END
$$;

-- Jadval bo'lmasa ham keyingi qadamlar ishlashi uchun bo'sh holda yaratamiz.
CREATE TABLE IF NOT EXISTS posts_legacy (
    id                  BIGINT PRIMARY KEY,
    created_at          TIMESTAMP,
    chat_id             VARCHAR(255),
    post_type           VARCHAR(255),
    route               VARCHAR(255),
    airport             VARCHAR(255),
    date                VARCHAR(255),
    contact             VARCHAR(255),
    telegram_user_name  VARCHAR(255),
    telegram_name       VARCHAR(255),
    baggage             VARCHAR(255),
    price               VARCHAR(255),
    comment             VARCHAR(255),
    step_number         INTEGER
);

-- Eski jadvalda ustun yetishmasa qo'shamiz (turli versiyalar bo'lishi mumkin).
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS created_at         TIMESTAMP;
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS chat_id            VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS post_type          VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS route              VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS airport            VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS date               VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS contact            VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS telegram_user_name VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS telegram_name      VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS baggage            VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS price              VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS comment            VARCHAR(255);
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS step_number        INTEGER;

-- Ko'chirish natijasini kuzatish uchun ustunlar.
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS migration_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS migration_note   TEXT;
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS migrated_post_id UUID;
ALTER TABLE posts_legacy ADD COLUMN IF NOT EXISTS migrated_at      TIMESTAMPTZ;

COMMENT ON TABLE posts_legacy IS
    'Eski bot ma''lumoti. O''chirilmaydi (CLAUDE.md 1.1). migration_status ustuni har bir qatorning yangi sxemaga ko''chganini ko''rsatadi.';

-- ---------------------------------------------------------------------
-- 2-qadam: foydalanuvchilar. chat_id butun son bo'lsa — telegram_id.
-- ---------------------------------------------------------------------
WITH legacy_users AS (
    SELECT
        chat_id::BIGINT                              AS telegram_id,
        max(NULLIF(btrim(telegram_user_name), ''))   AS username,
        max(NULLIF(btrim(telegram_name), ''))        AS first_name,
        min(created_at)                              AS first_seen,
        max(created_at)                              AS last_seen
    FROM posts_legacy
    WHERE chat_id ~ '^-?[0-9]{1,18}$'
    GROUP BY chat_id::BIGINT
)
INSERT INTO users (telegram_id, username, first_name, referral_source, first_seen_at, last_seen_at)
SELECT
    telegram_id,
    left(username, 64),
    left(first_name, 120),
    'legacy_bot',
    COALESCE(first_seen AT TIME ZONE 'UTC', now()),
    COALESCE(last_seen  AT TIME ZONE 'UTC', now())
FROM legacy_users
ON CONFLICT (telegram_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3-qadam: e'lonlar. Faqat to'liq to'ldirilgan va tanib olinadigan qatorlar.
--
-- Eski oqim e'lon yuborilgach qatorni DELETE qilardi (§2, 5-nuqson), shuning
-- uchun qolgan qatorlarning ko'pi — tashlab ketilgan draft. Ularni e'longa
-- aylantirmaymiz, `SKIPPED_INCOMPLETE` deb belgilaymiz.
--
-- `legacy_id` ustuni vaqtincha qo'shiladi: INSERT ... RETURNING orqali
-- qaysi yangi post qaysi eski qatordan chiqqanini aniq bog'lash uchun.
-- ---------------------------------------------------------------------
ALTER TABLE posts ADD COLUMN IF NOT EXISTS legacy_id BIGINT;

WITH parsed AS (
    SELECT
        l.id AS legacy_id,
        u.id AS user_id,
        CASE
            WHEN l.post_type ILIKE '%bervoraman%'   THEN 'SEND'
            WHEN l.post_type ILIKE '%olib ketaman%' THEN 'CARRY'
        END AS post_type,
        CASE
            WHEN l.route ILIKE '%yaponiyadan%'  THEN 'JP_UZ'
            WHEN l.route ILIKE '%zbekistondan%' THEN 'UZ_JP'
        END AS direction,
        (regexp_match(l.airport, '\(([A-Z]{3})\)'))[1]                    AS origin_code,
        (regexp_match(l.airport, '\(([A-Z]{3})\)[^(]*\(([A-Z]{3})\)'))[2] AS dest_code,
        l.airport                               AS raw_airport,
        NULLIF(btrim(l.date), '')               AS raw_date,
        NULLIF(btrim(l.price), '')              AS raw_price,
        NULLIF(btrim(l.baggage), '')            AS raw_baggage,
        NULLIF(btrim(l.comment), '')            AS raw_comment,
        NULLIF(btrim(l.contact), '')            AS raw_contact,
        NULLIF(btrim(l.telegram_user_name), '') AS raw_username,
        COALESCE(l.created_at AT TIME ZONE 'UTC', now()) AS created_at
    FROM posts_legacy l
    JOIN users u ON u.telegram_id = l.chat_id::BIGINT
    WHERE l.migration_status = 'PENDING'
      AND l.chat_id ~ '^-?[0-9]{1,18}$'
      AND l.post_type IS NOT NULL
      AND l.route     IS NOT NULL
      AND l.airport   IS NOT NULL
      AND l.date      IS NOT NULL
      AND l.contact   IS NOT NULL
), inserted AS (
    INSERT INTO posts (
        legacy_id, user_id, corridor_id, post_type, direction,
        origin_airport, dest_airport, origin_city_free, dest_city_free,
        comment, contact_phone, contact_telegram, contact_other,
        status, source, created_at, updated_at, expires_at
    )
    SELECT
        p.legacy_id,
        p.user_id,
        (SELECT id FROM corridors WHERE code = 'JP_UZ'),
        p.post_type,
        p.direction,
        ao.code,
        ad.code,
        CASE WHEN ao.code IS NULL THEN left(p.raw_airport, 120) END,
        CASE WHEN ad.code IS NULL THEN left(p.raw_airport, 120) END,
        -- Asl matnli maydonlar yo'qolmasligi uchun izohga ko'chiriladi.
        left(concat_ws(chr(10),
            p.raw_comment,
            CASE WHEN p.raw_baggage IS NOT NULL THEN 'Yuk (eski format): '  || p.raw_baggage END,
            CASE WHEN p.raw_date    IS NOT NULL THEN 'Sana (eski format): ' || p.raw_date    END,
            CASE WHEN p.raw_price   IS NOT NULL THEN 'Narx (eski format): ' || p.raw_price   END
        ), 1000),
        CASE WHEN p.raw_contact ~ '^[+0-9][0-9 ()-]{6,30}$' THEN left(p.raw_contact, 32) END,
        left(p.raw_username, 64),
        CASE WHEN p.raw_contact !~ '^[+0-9][0-9 ()-]{6,30}$' THEN left(p.raw_contact, 160) END,
        'EXPIRED',   -- eski e'lonlar qidiruvda chiqmaydi, lekin tarix sifatida qoladi
        'IMPORT',
        p.created_at,
        p.created_at,
        p.created_at
    FROM parsed p
    LEFT JOIN airports ao ON ao.code = p.origin_code
    LEFT JOIN airports ad ON ad.code = p.dest_code
    WHERE p.post_type IS NOT NULL
      AND p.direction IS NOT NULL
    RETURNING id, legacy_id
)
UPDATE posts_legacy l
SET migration_status = 'MIGRATED',
    migrated_post_id = i.id,
    migrated_at      = now(),
    migration_note   = 'V3 best-effort: sana va narx tipli ustunga ko''chirilmadi, izohda saqlandi'
FROM inserted i
WHERE l.id = i.legacy_id;

ALTER TABLE posts DROP COLUMN IF EXISTS legacy_id;

-- ---------------------------------------------------------------------
-- 4-qadam: ko'chirilmaganlarga sabab yozamiz. Qatorlar joyida qoladi.
-- ---------------------------------------------------------------------
UPDATE posts_legacy
SET migration_status = 'SKIPPED_NO_USER',
    migration_note   = 'chat_id butun son emas — foydalanuvchi yaratilmadi'
WHERE migration_status = 'PENDING'
  AND (chat_id IS NULL OR chat_id !~ '^-?[0-9]{1,18}$');

UPDATE posts_legacy
SET migration_status = 'SKIPPED_INCOMPLETE',
    migration_note   = 'To''ldirilmagan draft (eski 9 qadamli oqim tashlab ketilgan)'
WHERE migration_status = 'PENDING'
  AND (post_type IS NULL OR route IS NULL OR airport IS NULL
       OR date IS NULL OR contact IS NULL);

UPDATE posts_legacy
SET migration_status = 'SKIPPED_UNPARSEABLE',
    migration_note   = 'post_type yoki route tanib olinmadi'
WHERE migration_status = 'PENDING';

ALTER TABLE posts_legacy
    ADD CONSTRAINT chk_posts_legacy_migration_status
    CHECK (migration_status IN ('PENDING','MIGRATED','SKIPPED_INCOMPLETE','SKIPPED_NO_USER','SKIPPED_UNPARSEABLE'));
