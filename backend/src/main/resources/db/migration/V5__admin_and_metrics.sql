-- =====================================================================
-- V5__admin_and_metrics.sql — 4-bosqich: analitika va admin (CLAUDE.md §11, §6.3)
--
-- Uch qism:
--   1. Admin uchun jadvallar: bir martalik login kodi, sozlamalar (feature flag)
--   2. Kunlik agregatlar jadvali (`daily_metrics`) — raw event'lar 24 oydan
--      keyin tozalansa ham bu yerdagi raqamlar abadiy qoladi (§6.2)
--   3. Metrika view'lari (§6.3) — dashboard SHULARDAN o'qiydi, SQL
--      controller'ga tarqalmaydi
--
-- Hech qanday mavjud jadval o'zgartirilmaydi va hech narsa o'chirilmaydi (§1.1).
-- =====================================================================

-- =====================================================================
-- 1. ADMIN
-- =====================================================================

-- Bir martalik kirish kodi (§11.1). Bot beradi, admin panel almashtiradi.
-- Kodning o'zi saqlanmaydi — faqat SHA-256 hex (§1.2 ruhi: sir ochiq yotmaydi).
CREATE TABLE admin_login_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_admin_codes_lookup ON admin_login_codes(code_hash)
    WHERE used_at IS NULL AND deleted_at IS NULL;
CREATE INDEX idx_admin_codes_user ON admin_login_codes(user_id, created_at DESC);

-- Feature flag va sozlamalar (§11.2 — /settings sahifasi).
-- Qiymat JSONB: boolean, son va matn bir xil joyda saqlanadi.
CREATE TABLE app_settings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key    VARCHAR(64) UNIQUE NOT NULL,
    value_json     JSONB NOT NULL,
    value_type     VARCHAR(16) NOT NULL
        CHECK (value_type IN ('BOOLEAN','NUMBER','STRING')),
    title_uz       VARCHAR(160) NOT NULL,
    description_uz TEXT,
    updated_by     UUID REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ
);

INSERT INTO app_settings (setting_key, value_json, value_type, title_uz, description_uz) VALUES
    ('moderation.required', 'false'::jsonb, 'BOOLEAN', 'Moderatsiya majburiy',
     'Yoqilsa e''lon darhol kanalga chiqmaydi, admin tasdiqlashini kutadi.'),
    ('vip.enabled', 'false'::jsonb, 'BOOLEAN', 'VIP e''lon yoqilgan',
     '6-bosqich: Telegram Stars orqali pullik ko''tarish.'),
    ('rate_limit.posts_per_day', '5'::jsonb, 'NUMBER', 'Kuniga e''lon chegarasi',
     'Bitta foydalanuvchi bir kunda bera oladigan e''lonlar soni (§7.2).'),
    ('rate_limit.requests_per_minute', '60'::jsonb, 'NUMBER', 'Daqiqada so''rov chegarasi',
     'API so''rovlari chegarasi (§7.2).'),
    ('notifications.max_per_day', '5'::jsonb, 'NUMBER', 'Kuniga xabarnoma chegarasi',
     'Anti-spam: bitta foydalanuvchiga kuniga maksimal xabarnoma (§10.3).');

-- =====================================================================
-- 2. KUNLIK AGREGATLAR
--
-- Kalit-qiymat shakli ataylab: yangi metrika qo'shish uchun migratsiya
-- kerak bo'lmaydi, faqat job'ga bitta so'rov qo'shiladi.
-- `dimension` — kesim (masalan 'JP_UZ' yoki 'CARRY'), umumiy qiymat uchun ''.
-- =====================================================================
CREATE TABLE daily_metrics (
    id          BIGSERIAL PRIMARY KEY,
    metric_date DATE NOT NULL,
    metric_key  VARCHAR(64) NOT NULL,
    dimension   VARCHAR(64) NOT NULL DEFAULT '',
    value       NUMERIC(18,4) NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_daily_metrics_key
    ON daily_metrics(metric_date, metric_key, dimension);
CREATE INDEX idx_daily_metrics_lookup
    ON daily_metrics(metric_key, metric_date DESC);

-- =====================================================================
-- 3. METRIKA VIEW'LARI (§6.3)
--
-- Qoida: view parametr qabul qilmaydi — sana oralig'i chaqiruvchi tomonda
-- WHERE bilan qo'yiladi. Shunda bitta view ham dashboard'ga, ham kunlik
-- job'ga xizmat qiladi.
-- =====================================================================

-- --- E'lon oqimi: kuni, turi, yo'nalishi kesimida ------------------------
CREATE VIEW v_metrics_post_daily AS
SELECT
    date_trunc('day', p.created_at)::date               AS metric_date,
    p.post_type,
    p.direction,
    count(*)                                            AS created_count,
    count(*) FILTER (WHERE p.published_at IS NOT NULL)  AS published_count,
    count(*) FILTER (WHERE p.status = 'REJECTED')       AS rejected_count
FROM posts p
WHERE p.deleted_at IS NULL
GROUP BY 1, 2, 3;

-- --- Voronka (§6.3 "Form funnel") ---------------------------------------
-- Har bir qadamga yetgan NOYOB foydalanuvchilar soni. Qadam tartibi
-- `step_index` bilan qat'iy belgilangan — dashboard shu tartibda chizadi.
CREATE VIEW v_metrics_funnel_daily AS
WITH steps(step_key, step_index, event_name, step_filter) AS (
    VALUES
        ('form_open',   1, 'post_form_open',          NULL),
        ('step_1',      2, 'post_form_step_complete', '1'),
        ('step_2',      3, 'post_form_step_complete', '2'),
        ('step_3',      4, 'post_form_step_complete', '3'),
        ('step_4',      5, 'post_form_step_complete', '4'),
        ('preview',     6, 'post_preview_view',       NULL),
        ('safety_ok',   7, 'safety_checklist_accept', NULL),
        ('published',   8, 'post_publish_success',    NULL)
)
SELECT
    date_trunc('day', e.occurred_at)::date AS metric_date,
    s.step_key,
    s.step_index,
    count(DISTINCT e.user_id)              AS users_count,
    count(*)                               AS events_count
FROM steps s
JOIN events e
  ON e.event_name = s.event_name
 AND (s.step_filter IS NULL OR e.properties ->> 'step_index' = s.step_filter)
GROUP BY 1, 2, 3;

-- --- Tashlab ketish: oxirgi qadam kesimida ------------------------------
CREATE VIEW v_metrics_abandon_daily AS
SELECT
    date_trunc('day', e.occurred_at)::date             AS metric_date,
    coalesce(e.properties ->> 'last_step', 'unknown')  AS last_step,
    count(*)                                           AS abandon_count
FROM events e
WHERE e.event_name = 'post_form_abandon'
GROUP BY 1, 2;

-- --- Narx indeksi: oy x yo'nalish, valyuta/kg medianasi ------------------
-- PER_KG to'g'ridan-to'g'ri, TOTAL esa og'irlikka bo'linadi. NEGOTIABLE
-- indeksga kirmaydi — raqami yo'q.
CREATE VIEW v_metrics_price_index AS
SELECT
    date_trunc('month', p.published_at)::date AS metric_month,
    p.direction,
    p.origin_airport,
    p.dest_airport,
    p.price_currency,
    count(*)                                  AS sample_size,
    percentile_cont(0.5) WITHIN GROUP (
        ORDER BY CASE
            WHEN p.price_unit = 'PER_KG' THEN p.price_amount
            ELSE p.price_amount / nullif(p.weight_kg, 0)
        END
    )                                         AS median_per_kg
FROM posts p
WHERE p.deleted_at IS NULL
  AND p.published_at IS NOT NULL
  AND p.price_amount IS NOT NULL
  AND p.price_currency IS NOT NULL
  AND (p.price_unit = 'PER_KG' OR (p.price_unit = 'TOTAL' AND p.weight_kg > 0))
GROUP BY 1, 2, 3, 4, 5;

-- --- Talab/taklif balansi: hafta x koridor (§6.3) -----------------------
-- CARRY = taklif (joy bor), SEND = talab (yuk bor).
CREATE VIEW v_metrics_supply_demand AS
SELECT
    date_trunc('week', p.created_at)::date        AS metric_week,
    p.corridor_id,
    p.direction,
    count(*) FILTER (WHERE p.post_type = 'CARRY') AS carry_count,
    count(*) FILTER (WHERE p.post_type = 'SEND')  AS send_count,
    round(
        count(*) FILTER (WHERE p.post_type = 'CARRY')::numeric
        / nullif(count(*) FILTER (WHERE p.post_type = 'SEND'), 0), 3
    )                                             AS supply_demand_ratio
FROM posts p
WHERE p.deleted_at IS NULL
GROUP BY 1, 2, 3;

-- --- Fill rate: kamida bitta kontakt ochilgan e'lonlar ulushi ------------
CREATE VIEW v_metrics_fill_rate AS
SELECT
    date_trunc('month', p.published_at)::date      AS metric_month,
    p.direction,
    count(*)                                       AS published_count,
    count(*) FILTER (WHERE r.post_id IS NOT NULL)  AS filled_count,
    round(
        count(*) FILTER (WHERE r.post_id IS NOT NULL)::numeric
        / nullif(count(*), 0), 4
    )                                              AS fill_rate
FROM posts p
LEFT JOIN (SELECT DISTINCT post_id FROM contact_reveals) r ON r.post_id = p.id
WHERE p.deleted_at IS NULL AND p.published_at IS NOT NULL
GROUP BY 1, 2;

-- --- Match latency: publish -> birinchi kontakt ochilishi ----------------
CREATE VIEW v_metrics_match_latency AS
WITH first_reveal AS (
    SELECT post_id, min(created_at) AS first_reveal_at
    FROM contact_reveals
    GROUP BY post_id
)
SELECT
    date_trunc('month', p.published_at)::date AS metric_month,
    p.direction,
    count(*)                                  AS sample_size,
    percentile_cont(0.5) WITHIN GROUP (
        ORDER BY extract(epoch FROM (f.first_reveal_at - p.published_at)) / 60
    )                                         AS median_minutes
FROM posts p
JOIN first_reveal f ON f.post_id = p.id
WHERE p.deleted_at IS NULL AND p.published_at IS NOT NULL
GROUP BY 1, 2;

-- --- Natijasiz qidiruvlar: qoplanmagan talab (§11.2 eng qimmatli sahifa) -
CREATE VIEW v_metrics_zero_result_routes AS
SELECT
    coalesce(s.origin_airport, '?') AS origin_airport,
    coalesce(s.dest_airport, '?')   AS dest_airport,
    s.direction,
    s.post_type,
    count(*)                        AS search_count,
    max(s.created_at)               AS last_searched_at
FROM search_queries s
WHERE s.result_count = 0
GROUP BY 1, 2, 3, 4;

-- --- Qidiruv salomatligi: kunlik nol-natija ulushi -----------------------
CREATE VIEW v_metrics_search_daily AS
SELECT
    date_trunc('day', s.created_at)::date                  AS metric_date,
    count(*)                                               AS search_count,
    count(*) FILTER (WHERE s.result_count = 0)             AS zero_result_count,
    count(*) FILTER (WHERE s.clicked_post_id IS NOT NULL)  AS clicked_count,
    round(avg(s.latency_ms)::numeric, 1)                   AS avg_latency_ms
FROM search_queries s
GROUP BY 1;

-- --- Faol foydalanuvchilar -----------------------------------------------
CREATE VIEW v_metrics_active_users AS
SELECT
    date_trunc('day', e.occurred_at)::date AS metric_date,
    count(DISTINCT e.user_id)              AS dau
FROM events e
WHERE e.user_id IS NOT NULL
GROUP BY 1;

-- --- Kogorta retention (D1/D7/D30) ---------------------------------------
-- Kogorta = foydalanuvchi birinchi ko'ringan kun. Qaytish = o'sha kundan
-- N kun keyin biror event yozilgan bo'lsa.
CREATE VIEW v_metrics_cohort_retention AS
WITH cohort AS (
    SELECT u.id AS user_id, date_trunc('day', u.first_seen_at)::date AS cohort_date
    FROM users u
    WHERE u.deleted_at IS NULL
),
activity AS (
    SELECT DISTINCT e.user_id, date_trunc('day', e.occurred_at)::date AS active_date
    FROM events e
    WHERE e.user_id IS NOT NULL
)
SELECT
    c.cohort_date,
    count(DISTINCT c.user_id) AS cohort_size,
    count(DISTINCT c.user_id) FILTER (
        WHERE a.active_date = c.cohort_date + 1) AS d1,
    count(DISTINCT c.user_id) FILTER (
        WHERE a.active_date BETWEEN c.cohort_date + 1 AND c.cohort_date + 7) AS d7,
    count(DISTINCT c.user_id) FILTER (
        WHERE a.active_date BETWEEN c.cohort_date + 1 AND c.cohort_date + 30) AS d30
FROM cohort c
LEFT JOIN activity a ON a.user_id = c.user_id
GROUP BY 1;

-- --- Time-to-publish: forma ochilishidan kanalga chiqishgacha ------------
CREATE VIEW v_metrics_time_to_publish AS
SELECT
    date_trunc('day', e.occurred_at)::date AS metric_date,
    count(*)                               AS sample_size,
    percentile_cont(0.5) WITHIN GROUP (
        ORDER BY (e.properties ->> 'total_time_ms')::numeric
    ) / 1000                               AS median_seconds
FROM events e
WHERE e.event_name = 'post_publish_success'
  AND e.properties ->> 'total_time_ms' ~ '^[0-9]+$'
GROUP BY 1;

-- --- Takroriy e'lon beruvchilar (to'lovchi segment) ----------------------
CREATE VIEW v_metrics_repeat_posters AS
WITH monthly AS (
    SELECT
        date_trunc('month', p.created_at)::date AS metric_month,
        p.user_id,
        count(*)                                AS post_count
    FROM posts p
    WHERE p.deleted_at IS NULL
    GROUP BY 1, 2
)
SELECT
    metric_month,
    count(*)                                AS posters,
    count(*) FILTER (WHERE post_count >= 2) AS repeat_posters,
    round(count(*) FILTER (WHERE post_count >= 2)::numeric
          / nullif(count(*), 0), 4)         AS repeat_rate
FROM monthly
GROUP BY 1;

-- --- Mavsumiylik: hafta kuni va oy kesimida -----------------------------
CREATE VIEW v_metrics_seasonality AS
SELECT
    extract(isodow FROM p.created_at)::int AS day_of_week,
    extract(month FROM p.created_at)::int  AS month_of_year,
    p.post_type,
    count(*)                               AS post_count
FROM posts p
WHERE p.deleted_at IS NULL
GROUP BY 1, 2, 3;

-- --- Xabarnoma CTR (5-bosqichda to'ladi, view hozirdan tayyor) -----------
CREATE VIEW v_metrics_notifications AS
SELECT
    date_trunc('day', n.created_at)::date            AS metric_date,
    count(*)                                         AS sent_count,
    count(*) FILTER (WHERE n.status = 'FAILED')      AS failed_count,
    count(*) FILTER (WHERE n.opened_at IS NOT NULL)  AS opened_count,
    round(count(*) FILTER (WHERE n.opened_at IS NOT NULL)::numeric
          / nullif(count(*), 0), 4)                  AS ctr
FROM notifications_sent n
GROUP BY 1;

-- --- Shikoyat ulushi: xavfsizlik salomatligi -----------------------------
CREATE VIEW v_metrics_report_rate AS
SELECT
    date_trunc('month', r.created_at)::date        AS metric_month,
    count(*)                                       AS report_count,
    count(*) FILTER (WHERE r.status = 'OPEN')      AS open_count,
    count(*) FILTER (WHERE r.status = 'RESOLVED')  AS resolved_count
FROM reports r
GROUP BY 1;
