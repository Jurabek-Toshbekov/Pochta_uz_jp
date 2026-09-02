-- =====================================================================
-- V1__init.sql — asosiy sxema (CLAUDE.md §5.2)
--
-- Printsiplar (§5.1):
--   * har bir asosiy jadval: UUID PK, created_at, updated_at, deleted_at
--   * enum'lar VARCHAR + CHECK (raqamli enum taqiqlangan)
--   * pul NUMERIC(12,2) + currency, sana DATE/TIMESTAMPTZ
--   * events / search_queries / contact_reveals — append-only
--
-- gen_random_uuid() PostgreSQL 13+ da o'rnatilgan (pgcrypto kerak emas).
-- =====================================================================

-- ============ REFERENCE ============
CREATE TABLE airports (
    code            VARCHAR(4) PRIMARY KEY,        -- IATA: NRT, HND, KIX, NGO, FUK, TAS, SKD, BHK...
    country_code    VARCHAR(2)  NOT NULL,          -- JP, UZ, KR, RU...
    city_uz         VARCHAR(80) NOT NULL,
    city_ru         VARCHAR(80) NOT NULL,
    city_en         VARCHAR(80) NOT NULL,
    name_en         VARCHAR(160) NOT NULL,
    latitude        NUMERIC(9,6),
    longitude       NUMERIC(9,6),
    is_popular      BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order      INT NOT NULL DEFAULT 100,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE corridors (                            -- kelajakdagi kengayish uchun
    id              SMALLSERIAL PRIMARY KEY,
    code            VARCHAR(16) UNIQUE NOT NULL,    -- 'JP_UZ', 'KR_UZ', 'RU_UZ'
    origin_country  VARCHAR(2) NOT NULL,
    dest_country    VARCHAR(2) NOT NULL,
    title_uz        VARCHAR(120) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE cargo_categories (
    id              SMALLSERIAL PRIMARY KEY,
    code            VARCHAR(40) UNIQUE NOT NULL,    -- DOCUMENTS, SUPPLEMENTS, CLOTHES, ELECTRONICS, FOOD, MEDICINE, OTHER
    title_uz        VARCHAR(80) NOT NULL,
    title_ru        VARCHAR(80) NOT NULL,
    emoji           VARCHAR(8),
    risk_level      VARCHAR(12) NOT NULL DEFAULT 'LOW'   -- LOW | MEDIUM | HIGH
        CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
    warning_uz      TEXT,                            -- 'Yaponiyaga go'sht mahsulotlari taqiqlangan'
    sort_order      INT NOT NULL DEFAULT 100,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============ USERS ============
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_id         BIGINT UNIQUE NOT NULL,
    username            VARCHAR(64),
    first_name          VARCHAR(120),
    last_name           VARCHAR(120),
    language_code       VARCHAR(8),                  -- Telegram'dan
    ui_language         VARCHAR(8) NOT NULL DEFAULT 'uz',  -- uz | uz-cyrl | ru
    is_telegram_premium BOOLEAN NOT NULL DEFAULT FALSE,
    phone               VARCHAR(32),                 -- faqat foydalanuvchi ruxsati bilan
    phone_verified_at   TIMESTAMPTZ,
    role                VARCHAR(16) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER','MODERATOR','ADMIN')),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','LIMITED','BLOCKED')),
    blocked_reason      TEXT,
    verification_level  VARCHAR(16) NOT NULL DEFAULT 'NONE'
        CHECK (verification_level IN ('NONE','PHONE','DOCUMENT')),
    verified_at         TIMESTAMPTZ,
    trust_score         INT NOT NULL DEFAULT 0,
    consent_tos_at      TIMESTAMPTZ,                 -- foydalanish shartlariga rozilik
    consent_privacy_at  TIMESTAMPTZ,
    first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    referral_source     VARCHAR(120),                -- startapp= parametridan
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);
CREATE INDEX idx_users_telegram_id ON users(telegram_id);
CREATE INDEX idx_users_last_seen   ON users(last_seen_at DESC);

-- ============ POSTS ============
CREATE TABLE posts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    corridor_id         SMALLINT NOT NULL REFERENCES corridors(id),

    post_type           VARCHAR(12) NOT NULL          -- SEND: pochta bervoraman | CARRY: olib ketaman
        CHECK (post_type IN ('SEND','CARRY')),
    direction           VARCHAR(12) NOT NULL          -- JP_UZ | UZ_JP
        CHECK (direction IN ('JP_UZ','UZ_JP')),

    origin_airport      VARCHAR(4) REFERENCES airports(code),
    dest_airport        VARCHAR(4) REFERENCES airports(code),
    origin_city_free    VARCHAR(120),                 -- ro'yxatda yo'q bo'lsa
    dest_city_free      VARCHAR(120),
    final_destination   VARCHAR(120),                 -- 'Samarqand', 'Yokohama'

    depart_date         DATE,                         -- CARRY: uchish sanasi
    deadline_date       DATE,                         -- SEND: qachongacha kerak
    date_flexible_days  SMALLINT NOT NULL DEFAULT 0,  -- ±N kun

    weight_kg           NUMERIC(6,2),
    weight_kg_max       NUMERIC(6,2),
    price_amount        NUMERIC(12,2),
    price_currency      VARCHAR(3) CHECK (price_currency IN ('JPY','USD','UZS')),
    price_unit          VARCHAR(16)                   -- PER_KG | TOTAL | NEGOTIABLE
        CHECK (price_unit IN ('PER_KG','TOTAL','NEGOTIABLE')),

    comment             TEXT,
    contact_phone       VARCHAR(32),
    contact_telegram    VARCHAR(64),
    contact_other       VARCHAR(160),

    safety_checklist_ok BOOLEAN NOT NULL DEFAULT FALSE,
    safety_checked_at   TIMESTAMPTZ,

    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PENDING','PUBLISHED','REJECTED','EXPIRED','CLOSED','DELETED')),
    reject_reason       TEXT,
    is_promoted         BOOLEAN NOT NULL DEFAULT FALSE,   -- VIP (kelajakda pullik)
    promoted_until      TIMESTAMPTZ,

    channel_message_id  BIGINT,                       -- kanalga chiqqan post ID
    published_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    closed_reason       VARCHAR(32),                  -- FOUND | CANCELLED | EXPIRED

    view_count          INT NOT NULL DEFAULT 0,
    contact_reveal_count INT NOT NULL DEFAULT 0,
    search_hit_count    INT NOT NULL DEFAULT 0,

    source              VARCHAR(16) NOT NULL DEFAULT 'MINIAPP'
        CHECK (source IN ('MINIAPP','BOT','ADMIN','IMPORT')),

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);
CREATE INDEX idx_posts_search   ON posts(status, direction, post_type, depart_date)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_user     ON posts(user_id, created_at DESC);
CREATE INDEX idx_posts_pub      ON posts(published_at DESC) WHERE status = 'PUBLISHED';
CREATE INDEX idx_posts_route    ON posts(origin_airport, dest_airport);

CREATE TABLE post_categories (
    post_id     UUID NOT NULL REFERENCES posts(id),
    category_id SMALLINT NOT NULL REFERENCES cargo_categories(id),
    PRIMARY KEY (post_id, category_id)
);

-- To'ldirish jarayonining har bir saqlangan holati (draft autosave)
CREATE TABLE post_drafts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    payload     JSONB NOT NULL,
    step        VARCHAR(40),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_drafts_user ON post_drafts(user_id);

-- ============ ANALYTICS (append-only) ============
CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    event_name  VARCHAR(64) NOT NULL,
    user_id     UUID REFERENCES users(id),
    session_id  UUID,
    post_id     UUID,
    source      VARCHAR(16) NOT NULL          -- MINIAPP | BOT | CHANNEL | ADMIN | SYSTEM
        CHECK (source IN ('MINIAPP','BOT','CHANNEL','ADMIN','SYSTEM')),
    platform    VARCHAR(24),                  -- ios | android | tdesktop | web
    properties  JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_name_time ON events(event_name, occurred_at DESC);
CREATE INDEX idx_events_user      ON events(user_id, occurred_at DESC);
CREATE INDEX idx_events_props     ON events USING GIN (properties);

CREATE TABLE search_queries (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID REFERENCES users(id),
    session_id      UUID,
    post_type       VARCHAR(12),
    direction       VARCHAR(12),
    origin_airport  VARCHAR(4),
    dest_airport    VARCHAR(4),
    date_from       DATE,
    date_to         DATE,
    category_ids    SMALLINT[],
    price_max       NUMERIC(12,2),
    price_currency  VARCHAR(3),
    text_query      VARCHAR(200),
    result_count    INT NOT NULL,
    clicked_post_id UUID,
    latency_ms      INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_search_zero ON search_queries(created_at DESC) WHERE result_count = 0;

CREATE TABLE contact_reveals (
    id          BIGSERIAL PRIMARY KEY,
    post_id     UUID NOT NULL REFERENCES posts(id),
    viewer_id   UUID NOT NULL REFERENCES users(id),
    owner_id    UUID NOT NULL REFERENCES users(id),
    channel     VARCHAR(16) NOT NULL,          -- MINIAPP | CHANNEL
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reveal_post ON contact_reveals(post_id);

-- ============ NOTIFICATIONS ============
CREATE TABLE notification_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    post_type       VARCHAR(12),                -- nimani kutyapti (qarama-qarshi tomon)
    direction       VARCHAR(12),
    origin_airport  VARCHAR(4),
    dest_airport    VARCHAR(4),
    date_from       DATE,
    date_to         DATE,
    category_ids    SMALLINT[],
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE notifications_sent (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id UUID REFERENCES notification_subscriptions(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    post_id         UUID REFERENCES posts(id),
    status          VARCHAR(16) NOT NULL,       -- SENT | FAILED | BLOCKED
    opened_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ TRUST & SAFETY ============
CREATE TABLE reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id      UUID REFERENCES posts(id),
    reported_user_id UUID REFERENCES users(id),
    reporter_id  UUID NOT NULL REFERENCES users(id),
    reason       VARCHAR(32) NOT NULL,          -- SPAM | SCAM | PROHIBITED | OFFENSIVE | OTHER
    details      TEXT,
    status       VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','REVIEWING','RESOLVED','DISMISSED')),
    resolved_by  UUID REFERENCES users(id),
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE moderation_actions (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID NOT NULL REFERENCES users(id),
    target_type VARCHAR(16) NOT NULL,           -- POST | USER
    target_id   UUID NOT NULL,
    action      VARCHAR(32) NOT NULL,           -- APPROVE | REJECT | BLOCK | UNBLOCK | EDIT
    reason      TEXT,
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reviews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID REFERENCES posts(id),
    author_id   UUID NOT NULL REFERENCES users(id),
    subject_id  UUID NOT NULL REFERENCES users(id),
    rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE UNIQUE INDEX idx_review_unique ON reviews(post_id, author_id) WHERE deleted_at IS NULL;

-- ============ AUDIT ============
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(64) NOT NULL,
    entity      VARCHAR(40),
    entity_id   VARCHAR(64),
    payload     JSONB,
    ip_hash     VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
