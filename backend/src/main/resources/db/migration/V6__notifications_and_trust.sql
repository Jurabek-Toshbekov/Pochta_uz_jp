-- =====================================================================
-- V6__notifications_and_trust.sql — 5-bosqich: xabarnoma va ishonch
-- (CLAUDE.md §10.3, §6.4, §13)
--
-- Mavjud jadvallar o'zgartirilmaydi, faqat ustun va indeks qo'shiladi.
-- Hech narsa o'chirilmaydi (§1.1).
--
-- Uch narsa kerak:
--   1. Xabarnoma turini bilish (mos e'lon / "topildimi?" / muddat) va
--      har birini bir martadan ko'p yubormaslik
--   2. Bitim tasdiqlanganini yozib qo'yish — fill rate va GMV shundan
--   3. Ishonch ballini qayta hisoblash uchun kerakli indekslar
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Xabarnoma turi
--
-- `PENDING` holati qo'shiladi: xabarnoma darhol yuborilmaydi, avval
-- navbatga yoziladi. Shundan keyin digest job bir foydalanuvchiga
-- tegishli barcha navbatdagilarni BITTA xabarga birlashtiradi (§10.3
-- anti-spam). Aks holda 5 ta e'lon publish bo'lsa odam 5 ta xabar oladi.
-- ---------------------------------------------------------------------
ALTER TABLE notifications_sent
    ADD COLUMN kind VARCHAR(24) NOT NULL DEFAULT 'MATCH'
        CHECK (kind IN ('MATCH', 'DEAL_ASK', 'EXPIRY_WARNING', 'REVIEW_ASK'));

ALTER TABLE notifications_sent
    ADD CONSTRAINT chk_notifications_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'BLOCKED'));

-- Bitta e'lon uchun bitta odamga bitta turdagi xabar faqat bir marta.
-- Bu — idempotentlikning yagona kafolati: job qayta ishga tushsa ham
-- takroriy xabar ketmaydi.
CREATE UNIQUE INDEX idx_notifications_once
    ON notifications_sent(post_id, user_id, kind)
    WHERE post_id IS NOT NULL;

-- Kunlik chegarani (§10.3) tekshirish uchun.
CREATE INDEX idx_notifications_user_time
    ON notifications_sent(user_id, created_at DESC);

-- Digest job navbatni shu indeks bilan oladi.
CREATE INDEX idx_notifications_pending
    ON notifications_sent(created_at)
    WHERE status = 'PENDING';

-- ---------------------------------------------------------------------
-- 2. Obunani e'longa solishtirish
--
-- Moslik shartlari: yo'nalish, e'lon turi, aeroportlar, sana oralig'i,
-- kategoriyalar. Indeks eng tanlab beruvchi ustunlar bo'yicha.
-- ---------------------------------------------------------------------
CREATE INDEX idx_subs_match
    ON notification_subscriptions(direction, post_type)
    WHERE is_active = TRUE AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- 3. Bitim tasdiqlanishi (§6.4, 1-band)
--
-- "Odam topdingizmi?" savoliga javob shu yerga yoziladi. `closed_reason`
-- allaqachon bor (FOUND | CANCELLED | EXPIRED), lekin qachon tasdiqlangani
-- va kim bilan kelishilgani kerak — fill rate va reytting so'rovi uchun.
--
-- `deal_counterpart_id` — kontaktni ochgan odam. Aniq bilinmasa NULL
-- qoladi: taxmin qilib yozish ma'lumotni buzadi.
-- ---------------------------------------------------------------------
ALTER TABLE posts ADD COLUMN deal_confirmed_at   TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN deal_counterpart_id UUID REFERENCES users(id);

CREATE INDEX idx_posts_deal_followup
    ON posts(published_at)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

CREATE INDEX idx_posts_expiry
    ON posts(expires_at)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- 4. Reytting va ishonch balli (§13, 5-bosqich)
--
-- `reviews` jadvali V1 da bor. Bu yerda faqat o'qish indekslari:
-- ishonch balli har bir sharhdan keyin qayta hisoblanadi.
-- ---------------------------------------------------------------------
CREATE INDEX idx_reviews_subject
    ON reviews(subject_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_reviews_author
    ON reviews(author_id)
    WHERE deleted_at IS NULL;

-- Shikoyat oqimi uchun: ochiq shikoyatlarni foydalanuvchi kesimida sanash.
CREATE INDEX idx_reports_reported_user
    ON reports(reported_user_id, status);

-- Bitta odam bitta e'longa faqat bir marta shikoyat qila oladi.
-- Takroriy shikoyat metrikani buzadi va spam quroliga aylanadi.
CREATE UNIQUE INDEX idx_reports_once
    ON reports(post_id, reporter_id)
    WHERE post_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5. Metrika view'lari (§6.3)
-- ---------------------------------------------------------------------

-- Bitim tasdiqlash ulushi: publish bo'lgan e'lonlarning qanchasi
-- "odam topildi" bilan yopilgan. Fill rate'dan farqi — bu haqiqiy natija,
-- kontakt ochilishi esa faqat niyat.
CREATE VIEW v_metrics_deal_confirmation AS
SELECT
    date_trunc('month', p.published_at)::date        AS metric_month,
    p.direction,
    count(*)                                         AS published_count,
    count(*) FILTER (WHERE p.deal_confirmed_at IS NOT NULL) AS confirmed_count,
    round(count(*) FILTER (WHERE p.deal_confirmed_at IS NOT NULL)::numeric
          / nullif(count(*), 0), 4)                  AS confirmation_rate
FROM posts p
WHERE p.deleted_at IS NULL AND p.published_at IS NOT NULL
GROUP BY 1, 2;

-- Yopish sababi taqsimoti (§6.4, 3-band): "javob bo'lmadi" ko'p bo'lsa
-- bu mahsulot muammosi.
CREATE VIEW v_metrics_close_reasons AS
SELECT
    date_trunc('month', p.updated_at)::date AS metric_month,
    coalesce(p.closed_reason, 'UNKNOWN')    AS closed_reason,
    count(*)                                AS post_count
FROM posts p
WHERE p.deleted_at IS NULL AND p.status = 'CLOSED'
GROUP BY 1, 2;

-- Reytting taqsimoti va o'rtacha baho.
CREATE VIEW v_metrics_reviews AS
SELECT
    date_trunc('month', r.created_at)::date AS metric_month,
    count(*)                                AS review_count,
    round(avg(r.rating)::numeric, 2)        AS avg_rating,
    count(*) FILTER (WHERE r.rating <= 2)   AS negative_count
FROM reviews r
WHERE r.deleted_at IS NULL
GROUP BY 1;
