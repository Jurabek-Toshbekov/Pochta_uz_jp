# CLAUDE.md — Pochta_uz_jp

> Bu fayl loyihaning yagona haqiqat manbai (single source of truth).
> Claude Code har bir vazifadan oldin shu faylni o'qiydi va undagi qoidalarga so'zsiz amal qiladi.
> Kod English, izohlar va UI matnlari o'zbekcha.

---

## 0. TL;DR — nima qilinyapti

Telegram guruhidagi "e'lon formatlagich bot" **ma'lumotga asoslangan marketplace**ga aylantirilyapti.

| | Hozir | Bo'ladi |
|---|---|---|
| E'lon berish | Botda 9 qadamli savol-javob | **Telegram Mini App** (bitta chiroyli forma) |
| Bot roli | Formani to'ldiradi | **Faqat ma'lumot beradi + Mini App'ga yo'naltiradi** |
| Ma'lumot | Yuborilgach `DELETE` qilinadi | **Hech qachon o'chirilmaydi** (soft delete + event log) |
| Qidiruv | Yo'q | Mini App ichida filtrli qidiruv |
| Analitika | Yo'q | To'liq event tracking + admin dashboard |
| Admin | Yo'q | React dashboard (moderatsiya + analitika) |

---

## 1. MUTLAQ QOIDALAR (bularni buzish — xato)

1. **HECH QACHON `DELETE FROM` yozma.** Barcha o'chirish `deleted_at = now()` orqali (soft delete). Analitika event'lari — append-only, `UPDATE` ham qilinmaydi.
2. **Hech qanday sir kodda bo'lmaydi.** Bot token, DB parol, JWT secret — faqat environment variable. `.env` `.gitignore`da.
3. **`spring.jpa.hibernate.ddl-auto` doim `validate`.** Sxema faqat Flyway migratsiyalari orqali o'zgaradi. `create`/`update` — taqiqlangan.
4. **Mini App'dan kelgan har bir so'rov `initData` HMAC imzosi bilan tekshiriladi.** Tekshiruvsiz endpoint bo'lmaydi (`/health` dan tashqari).
5. **Foydalanuvchi kiritgan matnga hech qachon ishonma.** Sana → `LocalDate`, narx → `BigDecimal` + valyuta enum, aeroport → IATA kodi. String'da saqlash taqiqlangan.
6. **Har bir muhim foydalanuvchi harakati event sifatida yoziladi.** Yozilmagan harakat — yo'qolgan pul.
7. **PII (telefon, ism) hech qachon log'ga yozilmaydi.** Log'da faqat `user_id`.
8. **`e.printStackTrace()` ishlatilmaydi.** SLF4J logger.
9. **Har bir yangi funksiya migratsiya + test + event tracking bilan birga keladi.** Uchtasidan biri yo'q bo'lsa — vazifa tugamagan.
10. **Taqiqlangan buyumlar checklist'i majburiy.** Uni belgilamasdan e'lon publish bo'lmaydi. Bu funksiya olib tashlanmaydi va "keyinroq" qilinmaydi.

---

## 2. Hozirgi kodda tuzatilishi shart bo'lgan nuqsonlar

Claude Code birinchi navbatda shularni tuzatadi:

| # | Muammo | Fayl | Yechim |
|---|---|---|---|
| 1 | Bot token kodda, ochiq GitHub'da | `TelegramBotMain.java:206` | @BotFather'da `/revoke`, yangi token → `${BOT_TOKEN}` |
| 2 | DB paroli `1111` kodda | `application.properties` | `${DB_PASSWORD}` |
| 3 | `ddl-auto=create` — har restartda baza o'chadi | `application.properties` | `validate` + Flyway |
| 4 | `@CreatedDate` ishlamaydi → `createdAt` doim `null` | `PostEntity.java` | `@EntityListeners(AuditingEntityListener.class)` qo'shish |
| 5 | E'lon yuborilgach `delete` qilinadi | `TelegramBotMain.java:181` | Butunlay olib tashlash |
| 6 | Guruh chat_id kodda `-1001424117981` | `TelegramBotMain.java:171` | `${CHANNEL_CHAT_ID}` |
| 7 | "Puliga qarab hamma narsa 😅" tugmasi | `KeyboardLocal.java:170` | **O'chirish.** Huquqiy risk |
| 8 | Rasm yuborilsa 😡 emoji bilan javob | `MessageText.java` | Muloyim matnga almashtirish |
| 9 | Barcha maydonlar `String` | `PostEntity.java` | To'liq qayta modellashtirish (§5) |
| 10 | `e.printStackTrace()` (7 joyda) | hamma joyda | SLF4J |
| 11 | telegrambots 6.5.0 eski | `pom.xml` | 7.x/8.x ga ko'tarish |
| 12 | Testlar yo'q | — | Testcontainers + JUnit 5 |

---

## 3. Arxitektura

```
                    ┌──────────────────────────────┐
                    │   Telegram foydalanuvchisi   │
                    └───────┬──────────────┬───────┘
                            │              │
                   /start   │              │  WebApp tugmasi
                            ▼              ▼
                    ┌───────────┐   ┌──────────────────┐
                    │    BOT    │   │    MINI APP      │
                    │ (ma'lumot,│   │ React + Vite +TS │
                    │  yo'naltir│   │  - E'lon berish  │
                    │  xabarnoma│   │  - Qidiruv       │
                    └─────┬─────┘   │  - Mening e'lonl.│
                          │         └────────┬─────────┘
                          │                  │ REST + initData
                          ▼                  ▼
                 ┌────────────────────────────────────┐
                 │       SPRING BOOT BACKEND          │
                 │  bot / miniapp-api / admin-api     │
                 │  publisher / analytics / notifier  │
                 └──────────────┬─────────────────────┘
                                │
                 ┌──────────────▼─────────────────────┐
                 │      PostgreSQL (Flyway)           │
                 │  users · posts · events · searches │
                 └────────────────────────────────────┘
                                ▲
                                │ JWT (admin)
                 ┌──────────────┴─────────────────────┐
                 │   ADMIN DASHBOARD (React + Vite)   │
                 └────────────────────────────────────┘
                                │
                                ▼
                 ┌────────────────────────────────────┐
                 │   Telegram kanal @jpuzbpochta      │
                 └────────────────────────────────────┘
```

### Repo strukturasi (monorepo)

```
pochta-uz-jp/
├── CLAUDE.md
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/uz/pochtajp/
│       │   ├── PochtaApplication.java
│       │   ├── config/          # BotConfig, SecurityConfig, JacksonConfig, CorsConfig
│       │   ├── domain/          # Entity + enum
│       │   ├── repository/
│       │   ├── service/         # PostService, SearchService, PublishService...
│       │   ├── bot/             # BotHandler, commands, keyboards, texts
│       │   ├── api/miniapp/     # REST controller (Mini App uchun)
│       │   ├── api/admin/       # REST controller (Admin uchun)
│       │   ├── analytics/       # EventLogger, MetricsService
│       │   ├── security/        # TelegramInitDataValidator, JwtService
│       │   └── common/          # Exception handler, DTO, mapper, util
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/migration/    # V1__init.sql, V2__..., 
├── miniapp/                     # React + Vite + TS
│   └── src/{pages,components,api,hooks,store,i18n,styles}
├── admin/                       # React + Vite + TS
│   └── src/{pages,components,api,hooks,charts}
└── docs/
    ├── EVENTS.md                # event taksonomiyasi
    └── METRICS.md               # metrika ta'riflari
```

### Texnologiyalar

| Qism | Stack |
|---|---|
| Backend | Java 17, Spring Boot 3.3.x, Spring Data JPA, Flyway, Validation |
| DB | PostgreSQL 16 |
| Bot | telegrambots 7.x (webhook rejimida, dev'da long polling) |
| Mini App | React 18, Vite, TypeScript, TanStack Query, Zustand, `@twa-dev/sdk` |
| Admin | React 18, Vite, TypeScript, TanStack Query, TanStack Table, Recharts |
| Test | JUnit 5, Testcontainers, MockMvc, Vitest |
| Infra | Docker Compose, Nginx, Caddy (TLS) |

---

## 4. Environment variables

`.env.example` shu ko'rinishda bo'lsin (real qiymatlarsiz):

```bash
# --- Telegram ---
BOT_TOKEN=
BOT_USERNAME=uzb_jp_elon_bot
CHANNEL_CHAT_ID=-1001424117981
MINIAPP_URL=https://app.example.com
WEBHOOK_BASE_URL=https://api.example.com
WEBHOOK_SECRET_TOKEN=

# --- DB ---
DB_URL=jdbc:postgresql://localhost:5432/pochta
DB_USERNAME=pochta
DB_PASSWORD=

# --- Admin ---
ADMIN_JWT_SECRET=
ADMIN_TELEGRAM_IDS=123456789,987654321

# --- Boshqa ---
SPRING_PROFILES_ACTIVE=dev
INITDATA_MAX_AGE_SECONDS=86400
RATE_LIMIT_POSTS_PER_DAY=5
```

---

## 5. MA'LUMOT MODELI

> Bu — loyihaning yuragi. Ma'lumot to'g'ri saqlansa, qolgan hammasi keyin quriladi.
> Ma'lumot noto'g'ri saqlansa, hech qanday dashboard yordam bermaydi.

### 5.1 Asosiy printsiplar

- **Har bir jadval:** `id UUID PK`, `created_at`, `updated_at`, `deleted_at` (nullable).
- **Enum'lar** — PostgreSQL'da `VARCHAR` + `CHECK`, Java'da `@Enumerated(STRING)`. Raqamli enum taqiqlangan.
- **Reference data** (aeroportlar, kategoriyalar) — alohida jadvalda, ID orqali bog'lanadi. Hech qachon erkin matn emas.
- **Pul** — `NUMERIC(12,2)` + `currency` enum. `double` ishlatilmaydi.
- **Sana** — `DATE`/`TIMESTAMPTZ`. String taqiqlangan.
- Barcha `TIMESTAMPTZ` UTC'da saqlanadi, ko'rsatishda foydalanuvchi vaqt zonasiga o'giriladi.

### 5.2 Migratsiya `V1__init.sql` (asosiy sxema)

```sql
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
```

`V2__seed_reference.sql` — aeroportlar (NRT, HND, KIX, NGO, FUK, CTS, TAS, SKD, BHK, UGC, NMA, FEG), kategoriyalar, `JP_UZ` koridori.

### 5.3 Eski ma'lumotni ko'chirish

`V3__migrate_legacy.sql` — agar eski `post` jadvalida ma'lumot qolgan bo'lsa, uni `posts_legacy` nomiga o'zgartirib saqlash. **O'chirmaslik.** Keyin best-effort parser bilan yangi sxemaga ko'chirish, ko'chirilmaganlari `posts_legacy`da qoladi.

---

## 6. ANALITIKA — maksimal ma'lumot yig'ish

> Savol: "qanday qilib maksimal ma'lumot ko'rib, analiz qilsa bo'ladi?"
> Javob: **har bir foydalanuvchi harakatini event sifatida yozish + tuzilgan (typed) ma'lumot saqlash.**
> Erkin matnni analiz qilib bo'lmaydi; `depart_date DATE` va `price NUMERIC`ni bo'ladi.

### 6.1 Event taksonomiyasi (`docs/EVENTS.md`)

Har bir event: `event_name`, `user_id`, `session_id`, `source`, `platform`, `properties` (JSONB), `occurred_at`.

**Sessiya va kirish**
| Event | Properties |
|---|---|
| `app_open` | `start_param`, `platform`, `tg_version`, `color_scheme`, `is_first_open` |
| `app_close` | `duration_ms`, `screens_visited` |
| `bot_command` | `command` |
| `deep_link_open` | `start_param`, `resolved_post_id` |
| `language_changed` | `from`, `to` |

**E'lon berish voronkasi (eng muhim)**
| Event | Properties |
|---|---|
| `post_form_open` | `entry_point` (bot_button / search_empty / my_posts) |
| `post_form_step_view` | `step`, `step_index` |
| `post_form_step_complete` | `step`, `time_on_step_ms`, `edit_count` |
| `post_form_step_back` | `step`, `from_step` |
| `post_form_field_error` | `field`, `error_code` |
| `post_form_abandon` | `last_step`, `time_total_ms`, `filled_fields[]` |
| `post_draft_saved` | `step`, `completeness_pct` |
| `post_preview_view` | `post_id` |
| `safety_checklist_view` | — |
| `safety_checklist_accept` | `time_on_screen_ms` |
| `post_submit` | `post_id`, `post_type`, `direction`, `route`, `categories[]`, `price`, `currency`, `days_until_departure` |
| `post_publish_success` | `post_id`, `channel_message_id`, `total_time_ms` |
| `post_publish_fail` | `post_id`, `error_code` |
| `post_edit` | `post_id`, `changed_fields[]` |
| `post_close` | `post_id`, `reason`, `hours_since_publish` |

**Qidiruv (ikkinchi eng muhim)**
| Event | Properties |
|---|---|
| `search_open` | `entry_point` |
| `search_performed` | `filters{}`, `result_count`, `latency_ms` |
| `search_zero_result` | `filters{}` ← **qoplanmagan talab. Oltin ma'lumot.** |
| `search_filter_change` | `filter`, `value` |
| `search_result_click` | `post_id`, `position`, `result_count` |
| `search_saved` | `filters{}` → obunaga aylanadi |

**Qiymat momenti**
| Event | Properties |
|---|---|
| `post_view` | `post_id`, `source` |
| `post_detail_view` | `post_id`, `time_on_screen_ms` |
| `contact_reveal` | `post_id`, `owner_id`, `channel` ← **bitim boshlangan payt** |
| `contact_click` | `post_id`, `method` (telegram / phone) |
| `post_share` | `post_id`, `target` |
| `deal_confirmed` | `post_id`, `counterpart_id` (foydalanuvchi "topildi" bosganda) |
| `review_left` | `post_id`, `rating` |

**Xabarnoma**
`notification_sent`, `notification_opened`, `notification_converted` (ochib, kontakt ochgan)

**Xavfsizlik**
`report_submitted`, `post_rejected`, `user_blocked`, `rate_limit_hit`

### 6.2 Amalga oshirish

- Frontend'da `track(name, props)` helper. Batch: 10 event yoki 5 soniya → `POST /api/miniapp/events` (array).
- Backend'da `EventLogger` service. **Yozish hech qachon asosiy oqimni to'xtatmaydi** — `@Async` + try/catch.
- `session_id` — Mini App ochilganda generatsiya qilinadi, `sessionStorage`da saqlanadi.
- **Retention:** raw event'lar 24 oy. Kunlik agregatlar (`daily_metrics` materialized view) — abadiy.

### 6.3 Hosila metrikalar (`docs/METRICS.md`)

Bularni SQL view sifatida yozib qo'y — dashboard shulardan o'qiydi:

| Metrika | Ta'rif | Nima uchun muhim |
|---|---|---|
| **Form funnel** | Har bir qadamdan keyingi qadamga o'tish % | Qaysi qadam odamni qochiryapti |
| **Time-to-publish** | `post_form_open` → `post_publish_success` medianasi | UX sifati |
| **Abandon rate by step** | Tashlab ketish % qadam kesimida | Tuzatiladigan joy |
| **Supply/Demand ratio** | CARRY e'lonlar / SEND e'lonlar (koridor × hafta) | **Kimdan pul olish kerakligini shu aytadi** |
| **Fill rate** | ≥1 `contact_reveal` olgan e'lonlar % | Mahsulot ishlayaptimi |
| **Match latency** | `published_at` → birinchi `contact_reveal` medianasi | Tezlik = qiymat |
| **Price index** | ¥/kg medianasi (yo'nalish × oy) | Sotiladigan mahsulot. Kanalga post ham bo'ladi |
| **Zero-result rate** | Natijasiz qidiruvlar % | Qoplanmagan talab |
| **Top zero-result routes** | Natijasiz qidiruvdagi eng ko'p yo'nalishlar | Qayerga o'sish kerak |
| **Repeat poster rate** | 30 kunda ≥2 e'lon bergan % | Kuryerlar = to'lovchi segment |
| **D1/D7/D30 retention** | Kogorta bo'yicha qaytish | Mahsulot yopishqoqmi |
| **DAU / WAU / MAU** | Faol foydalanuvchilar | Umumiy salomatlik |
| **Seasonality** | Hafta/oy kesimida e'lon soni | Ta'til, Navro'z, yangi o'quv yili |
| **Notification CTR** | opened / sent | Xabarnoma spam emasmi |
| **Report rate** | shikoyat / e'lon | Xavfsizlik salomatligi |

### 6.4 Ma'lumotni ko'paytiruvchi UX yechimlari

Bular funksiya emas — **ma'lumot ishlab chiqaruvchi mexanizmlar**. Har birini albatta qil:

1. **"Topildimi?" so'rovi.** E'lon publish bo'lgandan 3 kun keyin bot so'raydi: "Odam topdingizmi?" → `deal_confirmed`. Bu — GMV va fill rate'ni bilishning yagona yo'li.
2. **Kontaktni ochish tugmasi.** Kontakt darhol ko'rinmasin — "Bog'lanish" bosilganda ochilsin. Foydalanuvchiga qulaylik yo'qolmaydi, sizga esa **niyat signali** qoladi.
3. **Yopish sababi.** E'lonni yopganda: "Odam topildi / Rejam o'zgardi / Javob bo'lmadi". Uchinchi javob — mahsulot muammosi haqida.
4. **Natijasiz qidiruvda taklif.** "Bu yo'nalishda hozir e'lon yo'q. Chiqsa xabar beraymi?" → obuna + `search_zero_result`.
5. **Draft autosave.** Har o'zgarishda saqla. Tashlab ketilgan draft — voronkadagi teshikning fotosurati.
6. **Deep link atributsiyasi.** Kanaldagi har bir post havolasida `?startapp=ch_<post_id>`. Qayerdan kelganini bilasiz.
7. **Reytting so'rovi.** `deal_confirmed` dan keyin — 1-5 baho. Trust score shundan quriladi.
8. **Narx birligini majburiy qil.** "2000 yen" emas, `2000 + JPY + PER_KG`. Faqat shu narsa narx indeksini mumkin qiladi.

---

## 7. XAVFSIZLIK

### 7.1 Telegram initData validatsiyasi (majburiy)

Har bir `/api/miniapp/**` so'rovida `Authorization: tma <initDataRaw>` header bo'ladi.

`TelegramInitDataValidator` quyidagini qiladi:
1. `initData` ni query-string sifatida parse qiladi, `hash` ni ajratadi.
2. Qolgan juftliklarni kalit bo'yicha alifbo tartibida saralab, `\n` bilan birlashtiradi (`data_check_string`).
3. `secret_key = HMAC_SHA256(key="WebAppData", data=BOT_TOKEN)`.
4. `computed = HMAC_SHA256(key=secret_key, data=data_check_string)` → hex.
5. `computed == hash` bo'lmasa → **401**.
6. `auth_date` `INITDATA_MAX_AGE_SECONDS` dan eski bo'lsa → **401**.
7. `user.id` dan `users` yozuvini topadi/yaratadi, `SecurityContext`ga qo'yadi.

**Ushbu tekshiruvsiz birorta ham endpoint bo'lmasin.** `user_id` ni request body'dan olish — **qat'iy taqiqlangan**.

### 7.2 Boshqa talablar

- **Rate limiting:** 5 e'lon/kun/foydalanuvchi, 60 API so'rov/daqiqa. Oshsa `429` + `rate_limit_hit` event.
- **Webhook:** `X-Telegram-Bot-Api-Secret-Token` header tekshiriladi.
- **Admin API:** JWT, `role IN (MODERATOR, ADMIN)`. Login — Telegram Login Widget yoki bot orqali bir martalik kod.
- **CORS:** faqat `MINIAPP_URL` va admin domeni.
- **Input:** har bir DTO'da Bean Validation. `comment` maksimal 1000 belgi, HTML escape.
- **Telegram HTML:** kanalga chiqishdan oldin `<`, `>`, `&` escape qilinadi.
- **Log:** telefon/ism yozilmaydi. IP saqlansa — faqat hash.
- **Maxfiylik:** birinchi kirishda ToS + Privacy roziligi olinadi, `consent_*_at` ga yoziladi. Foydalanuvchi o'z ma'lumotini eksport qilish va o'chirishni so'ray oladi (`/mening_malumotlarim`).

### 7.3 Taqiqlangan buyumlar himoyasi

Publish'dan oldingi majburiy ekran. Foydalanuvchi 3 ta katakchani belgilamaguncha "Yuborish" tugmasi o'chiq turadi:

- [ ] Men yopiq/o'ralgan qutini olmayman — yuk egasi oldida ochib ko'raman.
- [ ] Yukda taqiqlangan buyum yo'qligiga ishonch hosil qildim.
- [ ] Bu e'lon uchun to'liq javobgarlikni o'zim olaman.

Yonida ochiladigan ro'yxat: giyohvand va psixotrop moddalar, o'q-dori, go'sht va sut mahsulotlari (Yaponiyaga kirish taqiqlangan), retseptli dorilar va tarkibida psevdoefedrin bo'lgan preparatlar, ko'p miqdordagi naqd pul, o'simlik va urug'lar, soxta brend mahsulotlar.

`HIGH` risk kategoriyasi tanlansa (`MEDICINE`, `FOOD`, `SUPPLEMENTS`), o'sha kategoriyaning `warning_uz` matni alohida ko'rsatiladi.

Har bir e'londa "⚠️ Shikoyat qilish" tugmasi bo'ladi.

---

## 8. BOTNING YANGI ROLI

Bot **endi forma to'ldirmaydi**. Uning ishi: kutib olish, tushuntirish, yo'naltirish, xabar berish.

### 8.1 Buyruqlar

| Buyruq | Nima qiladi |
|---|---|
| `/start` | Salomlashish + qisqa tushuntirish + WebApp tugmasi "📝 E'lon berish" |
| `/elon` | To'g'ridan-to'g'ri Mini App'ni e'lon berish ekranida ochadi |
| `/qidiruv` | Mini App'ni qidiruv ekranida ochadi |
| `/mening_elonlarim` | Mini App'ni "Mening e'lonlarim" ekranida ochadi |
| `/obuna` | Xabarnoma obunalarini boshqarish |
| `/xavfsizlik` | Taqiqlangan buyumlar va xavfsizlik qoidalari |
| `/qoidalar` | Guruh qoidalari va foydalanish shartlari |
| `/til` | uz-latn / uz-cyrl / ru |
| `/yordam` | Ko'p so'raladigan savollar |
| `/mening_malumotlarim` | Ma'lumot eksporti / o'chirish so'rovi |

### 8.2 `/start` matni (aynan shu ruh)

```
Assalomu alaykum, {ism}!

Bu bot Yaponiya ↔ O'zbekiston yo'nalishida pochta yuborish va
olib ketish e'lonlari uchun.

Qanday ishlaydi:
1. Quyidagi tugmani bosing — ilova ochiladi
2. 1 daqiqada e'lonni to'ldirasiz
3. E'lon @jpuzbpochta kanaliga chiqadi

Kanaldan yoki ilova ichidagi qidiruvdan o'zingizga mos
odamni topasiz.

⚠️ Muhim: pochta olayotganda yopiq qutini qabul qilmang.
Batafsil: /xavfsizlik

[ 📝 E'lon berish ]   ← WebApp tugmasi
[ 🔍 Qidirish ]        [ 📋 Mening e'lonlarim ]
```

### 8.3 Bot boshqa nimalar qiladi

- **Xabarnoma yuborish.** Obuna bo'lgan foydalanuvchiga mos e'lon chiqsa xabar beradi (deep link bilan).
- **"Topildimi?" so'rovi.** Publish'dan 3 kun keyin.
- **E'lon muddati tugashi haqida ogohlantirish.** 1 kun oldin.
- **Kanal'ga publish qilish.** Post matnini shakllantirib yuboradi, `channel_message_id` ni saqlaydi.
- **Erkin matnga javob.** Foydalanuvchi shunchaki matn yozsa: "Men buyruqlar bilan ishlayman. E'lon berish uchun quyidagi tugmani bosing." + tugma. **Hech qanday jerkish, hech qanday 😡.**
- **Media yuborilsa:** "Rasm va fayllarni ilova orqali yuborasiz. Pastdagi tugmani bosing 🙂"

### 8.4 Kanal post shabloni

```html
<b>{emoji} {POST_TYPE_TITLE}</b>

<b>Yo'nalish:</b> {ORIGIN_CODE} → {DEST_CODE}  ({origin_city} → {dest_city})
<b>Sana:</b> {date}{, ±N kun}
<b>Yuk:</b> {categories}{, N kg gacha}
<b>Narx:</b> {amount} {currency} / {unit}
<b>Izoh:</b> {comment}

{✅ Tasdiqlangan foydalanuvchi | (belgi bo'lmasa hech narsa)}

{hashtags}

👉 Bog'lanish va batafsil: {deep_link}
📝 E'lon berish: @uzb_jp_elon_bot
```

`deep_link` = `https://t.me/uzb_jp_elon_bot/app?startapp=ch_{post_id}` — bosilganda Mini App shu e'lon sahifasida ochiladi, `deep_link_open` event yoziladi. **Kontakt kanalda ko'rsatilmaydi** — bu atributsiyani va reveal metrikasini beradi, spam va scraping'ni kamaytiradi.

---

## 9. MINI APP

### 9.1 Ekranlar

```
/                     Bosh sahifa: 2 ta katta tugma + oxirgi e'lonlar
/new                  E'lon berish (4 qadam)
/new/preview          Ko'rib chiqish + xavfsizlik checklist
/search               Qidiruv + filtrlar
/post/:id             E'lon tafsiloti + "Bog'lanish"
/my                   Mening e'lonlarim (faol / yopilgan)
/my/:id/edit          Tahrirlash
/subscriptions        Xabarnoma obunalari
/profile              Til, reytting, ma'lumotlarim
```

### 9.2 E'lon berish oqimi — 4 qadam

Bot'dagi 9 qadam → 4 qadam. Har bir ekranda progress bar va `BackButton`.

**1-qadam — Nima qilmoqchisiz**
- Ikkita katta karta: `📦 Pochta yubormoqchiman` / `✈️ Pochta olib ketaman`
- Yo'nalish: `🇯🇵 → 🇺🇿` / `🇺🇿 → 🇯🇵` (segment control)

**2-qadam — Yo'nalish va sana**
- Aeroport tanlash: qidiruvli ro'yxat (mashhurlari tepada), "Boshqa" → erkin matn
- Yakuniy manzil (ixtiyoriy): "Samarqand"
- **Sana — kalendar** (erkin matn emas). CARRY → uchish sanasi. SEND → oxirgi muddat.
- "±3 kun moslasha olaman" checkbox

**3-qadam — Yuk va narx**
- Kategoriya chip'lari (ko'p tanlov). `HIGH` risk tanlansa — ogohlantirish darhol ko'rinadi
- Og'irlik: slider yoki input (kg)
- Narx: raqam + valyuta toggle (¥ / $ / so'm) + birlik toggle (kg uchun / jami / kelishamiz)
- Izoh (maks 1000 belgi, hisoblagich bilan)

**4-qadam — Aloqa**
- Telegram username avtomatik olinadi (`initData`dan) — ko'rsatiladi, o'zgartirsa bo'ladi
- Telefon: Telegram `requestContact` tugmasi yoki qo'lda
- "Kontaktim faqat 'Bog'lanish' bosilganda ko'rinadi" — tushuntirish

**Preview + Xavfsizlik**
- E'lon aynan kanalda chiqadigan ko'rinishda ko'rsatiladi
- 3 ta majburiy checkbox (§7.3)
- `MainButton`: "Kanalga yuborish"

**Muvaffaqiyat ekrani**
- ✅ + "E'loningiz chiqdi" + kanaldagi postga havola + "Ulashish" tugmasi

### 9.3 Telegram SDK talablari

- `WebApp.ready()`, `WebApp.expand()`
- `WebApp.MainButton` — asosiy harakat uchun (o'z tugmangizni yasamang)
- `WebApp.BackButton` — qadamlar orasida
- `WebApp.HapticFeedback` — tanlov va yuborishda
- `WebApp.themeParams` — fon va matn ranglari Telegram temasidan olinadi
- `WebApp.showConfirm` — yopish/bekor qilishda
- `WebApp.CloudStorage` — til tanlovi va draft ID uchun
- `startapp` parametri o'qiladi va marshrutga aylantiriladi

### 9.4 Dizayn yo'nalishi

**Kontseptsiya: "boarding pass".** E'lon — bu aslida chipta. Karta boarding pass ko'rinishida: chap tomonda yo'nalish strip'i (`NRT ──✈── TAS`), o'ngda sana va narx, pastda perforatsiya chizig'i (nuqtali border) va yuk kategoriyalari. Publish bo'lganda karta ustiga **muhr (hanko)** bosilgandek animatsiya — bu loyihaning esda qoladigan yagona elementi. Boshqa hamma joy jim va intizomli.

**Ranglar** (Telegram temasi ustidan, brend tokenlari):
```css
--ink:      #101A2B;   /* asosiy matn, quyuq yuzalar — indigo tun */
--indigo:   #2C4A8C;   /* asosiy harakat tugmasi (suzani indigosi) */
--hanko:    #D2352C;   /* muhr qizili — FAQAT publish muhri va o'chirish uchun */
--mist:     #7C8899;   /* ikkilamchi matn */
--paper:    #F2F4F7;   /* sovuq qog'oz, och tema foni */
--line:     #DCE1E8;   /* chegaralar, perforatsiya */
```
Qorong'i temada `--paper` va `--ink` almashadi; `--indigo` `#5B7FD1` ga yorishadi.
`hanko` rangini boshqa hech qayerda ishlatmang — u kam ishlatilgani uchun kuchli.

**Shriftlar:**
- UI/matn: **Onest** (lotin + kirill bor, 400/500/700)
- Kod va raqamlar (aeroport kodlari, sanalar, og'irlik, narx): **JetBrains Mono** 500 — aeroport tablosi hissi
- IATA kodlari doim `letter-spacing: 0.08em` bilan, katta harfda

**Tipografiya shkalasi:** 12 / 14 / 16 / 20 / 28 / 40. Sarlavhalar 700, tana 400, mono 500.

**Layout:** bitta ustun, 16px chetki bo'shliq, kartalar orasida 12px, `border-radius: 14px`. Perforatsiya chizig'i — `repeating-linear-gradient` bilan nuqtali.

**Harakat:** faqat ikki joyda — qadamlar orasida gorizontal siljish (200ms) va publish muhri (400ms, `scale(1.4) → 1` + `opacity`). Boshqa hech qanday animatsiya yo'q. `prefers-reduced-motion` hurmat qilinadi.

**Matn qoidalari:** tugma nima qilishini aytadi ("Kanalga yuborish", "Yuborish" emas). Xatolik hech qachon kechirim so'ramaydi, nima bo'lganini va nima qilish kerakligini aytadi ("Sana o'tib ketgan. Bugundan keyingi kunni tanlang."). Bo'sh ekran — taklif ("Hali e'lon bermagansiz. Birinchisini yarataylik.").

### 9.5 Sifat minimumi

- Mobil birinchi navbatda (360px dan boshlab)
- Klaviatura fokusi ko'rinadigan
- Har bir input `aria-label` bilan
- Offline/xato holati — qayta urinish tugmasi bilan
- Draft avtomatik saqlanadi, ilova yopilib ochilsa o'sha joydan davom etadi
- Birinchi yuklanish < 200KB gzip

---

## 10. QIDIRUV (2-bosqich)

### 10.1 Filtrlar

| Filtr | Turi |
|---|---|
| E'lon turi | SEND / CARRY / hammasi |
| Yo'nalish | JP→UZ / UZ→JP |
| Chiqish aeroporti | ko'p tanlov |
| Kelish aeroporti | ko'p tanlov |
| Sana oralig'i | dan — gacha |
| Kategoriya | ko'p tanlov |
| Maksimal narx | raqam + valyuta |
| Faqat tasdiqlangan | toggle |

Saralash: `Eng yangi` (default) · `Uchish sanasi bo'yicha` · `Arzon` · `Reytting`.

### 10.2 Texnik

- `GET /api/miniapp/posts?type=&direction=&from=&to=&dateFrom=&dateTo=&categories=&priceMax=&currency=&verifiedOnly=&sort=&page=&size=`
- Faqat `status='PUBLISHED' AND deleted_at IS NULL AND expires_at > now()`
- Keyset pagination (`published_at` + `id`), offset emas
- Matn qidiruvi: `comment` ustida `tsvector` (`simple` konfiguratsiyasi + `unaccent`), alohida ustunda saqlanadi, trigger bilan yangilanadi
- **Har bir qidiruv `search_queries` jadvaliga yoziladi** — natija soni va latency bilan
- Natija 0 bo'lsa → `search_zero_result` event + "Xabar berishimni xohlaysizmi?" taklifi

### 10.3 Saqlangan qidiruv → obuna

Qidiruv natijasida "🔔 Bu qidiruvni saqlash" tugmasi. Bosilsa `notification_subscriptions`ga yoziladi. Yangi e'lon publish bo'lganda `NotificationService` mos obunalarni topib, botga xabar yuboradi.

Anti-spam: bir foydalanuvchiga kuniga maksimal 5 ta xabarnoma, o'xshash e'lonlar birlashtiriladi ("Sizga mos 3 ta yangi e'lon bor").

---

## 11. ADMIN DASHBOARD (React)

### 11.1 Kirish

Telegram Login Widget yoki bot orqali bir martalik kod → backend JWT beradi (2 soat, refresh bilan). `ADMIN_TELEGRAM_IDS` ro'yxatidagilar avtomatik `ADMIN` rolini oladi.

### 11.2 Sahifalar

**`/` — Umumiy ko'rinish**
- KPI kartalari: bugungi e'lon, DAU, publish konversiyasi, fill rate, ochiq shikoyatlar, natijasiz qidiruv %
- Chiziqli grafik: 30 kunlik e'lon soni (SEND vs CARRY alohida chiziq)
- Ustunli grafik: yo'nalish bo'yicha taqsimot
- Voronka: `form_open → step1 → step2 → step3 → step4 → preview → publish`

**`/posts` — Moderatsiya**
- Jadval: sana, foydalanuvchi, tur, yo'nalish, kategoriya, narx, status
- Filtrlar + tezkor harakatlar: Tasdiqlash / Rad etish (sabab bilan) / Tahrirlash / Yopish
- `PENDING` va `HIGH` risk kategoriyalilar tepada
- Har bir harakat `moderation_actions`ga yoziladi

**`/users` — Foydalanuvchilar**
- Jadval: ism, username, e'lon soni, reytting, shikoyat soni, status, oxirgi faollik
- Profil sahifasi: to'liq tarix, e'lonlari, sharhlari, event timeline
- Harakatlar: Bloklash / Cheklash / Tasdiqlash (verification level)

**`/reports` — Shikoyatlar**
- Ochiq shikoyatlar navbati, sabab bo'yicha guruhlangan
- Ko'rib chiqish → hal qilish (harakat + izoh)

**`/analytics` — Analitika**
- Voronka (qadam kesimida tashlab ketish %)
- Kogorta retention jadvali (D1/D7/D30)
- **Narx indeksi:** yo'nalish × oy, mediana ¥/kg — chiziqli grafik
- **Talab/taklif balansi:** koridor × hafta, CARRY/SEND nisbati — issiqlik xaritasi
- Match latency taqsimoti
- Mavsumiylik: hafta kunlari va oylar kesimida

**`/search-insights` — Qidiruv tahlili** *(eng qimmatli sahifa)*
- Natijasiz qidiruvlar top-50 (yo'nalish va sana bo'yicha)
- Eng ko'p qidiriladigan yo'nalishlar vs eng ko'p e'lon bor yo'nalishlar (yonma-yon)
- Qidiruv → bosish konversiyasi

**`/notifications`** — yuborilgan xabarnomalar, CTR, obunalar statistikasi

**`/settings`** — feature flag'lar (VIP yoqilganmi, moderatsiya majburiymi, rate limitlar), reference data tahriri (aeroport, kategoriya qo'shish)

**`/audit`** — audit log, filtrlanadigan

### 11.3 Admin dizayni

Mini App'dan boshqacha vazifa: bu — **boshqaruv minorasi**, ko'p ma'lumot, kam bezak.

- Bir xil ranglar tokenlari, lekin quyuq navigatsiya paneli (`--ink`) + oq kontent maydoni
- **Barcha raqamlar JetBrains Mono** — ustunlar tik tekislanadi
- Jadval qatorlari 36px, hover'da `--paper` fon
- Grafiklar: Recharts, faqat 3 rang (`--indigo` asosiy seriya, `--mist` taqqoslash, `--hanko` ogohlantirish)
- Hech qanday gradient, hech qanday soya. Faqat hairline chiziqlar (`--line`)
- KPI kartada: katta mono raqam + ostida kichik o'zgarish ko'rsatkichi (↑ 12% — o'tgan haftaga nisbatan)
- Har bir grafik ostida bitta jumlalik izoh: bu raqam nimani anglatadi

---

## 12. API — endpointlar ro'yxati

### Mini App (`/api/miniapp`, initData auth)
```
POST   /session                    initData tekshiruvi, user upsert, profil qaytaradi
GET    /reference                  aeroportlar, kategoriyalar, koridorlar (cache 1 soat)
GET    /posts                      qidiruv (§10.2)
GET    /posts/{id}                 tafsilot (kontaktsiz)
POST   /posts/{id}/reveal-contact  kontaktni ochadi + contact_reveals yozadi
POST   /posts                      e'lon yaratish (status=PENDING yoki PUBLISHED)
PATCH  /posts/{id}                 tahrirlash (faqat egasi)
POST   /posts/{id}/close           yopish (sabab bilan)
GET    /my/posts                   mening e'lonlarim
GET    /drafts                     joriy draft
PUT    /drafts                     draft saqlash
DELETE /drafts                     draftni tashlash
GET    /subscriptions
POST   /subscriptions
DELETE /subscriptions/{id}
POST   /reports                    shikoyat
POST   /reviews                    baho berish
POST   /events                     event batch (array)
GET    /me                         profil
PATCH  /me                         til, telefon
GET    /me/export                  ma'lumot eksporti (JSON)
```

### Admin (`/api/admin`, JWT auth)
```
POST   /auth/telegram              login
GET    /overview                   KPI to'plami
GET    /posts                      filtrlangan ro'yxat
POST   /posts/{id}/approve
POST   /posts/{id}/reject
PATCH  /posts/{id}
GET    /users
GET    /users/{id}
POST   /users/{id}/block
POST   /users/{id}/verify
GET    /reports
POST   /reports/{id}/resolve
GET    /analytics/funnel?from=&to=
GET    /analytics/cohorts
GET    /analytics/price-index?route=&from=&to=
GET    /analytics/supply-demand
GET    /search-insights/zero-results
GET    /notifications/stats
GET    /settings
PATCH  /settings
GET    /audit
```

### Webhook
```
POST   /webhook/telegram/{secret}
GET    /health
```

---

## 13. BOSQICHLAR VA VAZIFALAR

> Har bir bosqich tugagach: migratsiya ishlaydi, testlar yashil, event'lar yozilyapti, README yangilangan.
> Bosqichni tugatmasdan keyingisiga o'tilmaydi.

### 0-bosqich — Poydevor (xavfsizlik + ma'lumot)
- [x] Monorepo strukturasini yaratish, `.gitignore`, `.env.example`
- [x] `docker-compose.yml` (postgres + backend; miniapp/admin bloklari 1- va 4-bosqichda ochiladi)
- [x] Barcha sirlarni env'ga chiqarish; git tarixini tozalash bo'yicha README'da yo'riqnoma
- [x] Flyway ulash, `ddl-auto=validate`
- [x] `V1__init.sql` — to'liq sxema (§5.2)
- [x] `V2__seed_reference.sql` — aeroportlar, kategoriyalar, koridor
- [x] `V3__migrate_legacy.sql` — eski ma'lumotni `posts_legacy`ga saqlash
- [x] Entity + Repository + enum'lar
- [x] `EventLogger` service + `POST /api/miniapp/events`
- [x] SLF4J logging, `@ControllerAdvice` global exception handler
- [x] Testcontainers bilan integratsiya testi bazasi
- [x] Eski bot kodidan: "hamma narsa" tugmasini, 😡 ni, `delete` ni olib tashlash (butun eski paket o'chirildi)

### 1-bosqich — Mini App (e'lon berish)
- [x] `TelegramInitDataValidator` + Spring Security filtri (0-bosqichda bajarildi — §1.4 talab qildi)
- [x] `/api/miniapp/session`, `/reference`, `/posts` (POST), `/drafts` (+ `/my/posts`)
- [x] React + Vite + TS skeleton, `@twa-dev/sdk` ulash
- [x] Dizayn tokenlari (§9.4), shriftlar, boarding-pass karta komponenti
- [x] 4 qadamli forma + validatsiya + draft autosave
- [x] Xavfsizlik checklist ekrani (§7.3)
- [x] Preview + publish → `PublishService` kanalga yuboradi
- [x] Muvaffaqiyat ekrani + ulashish (hanko muhri animatsiyasi bilan)
- [x] i18n: uz-latn / uz-cyrl / ru
- [x] Voronka event'lari to'liq ulangan

### 2-bosqich — Botni soddalashtirish
- [x] Barcha step-based kod olib tashlanadi (`stepNumber` mantiqi butunlay)
- [x] Yangi buyruqlar (§8.1), matnlar (§8.2) — uch tilda
- [x] WebApp tugmalari (Mini App HashRouter marshrutlariga to'g'ridan-to'g'ri)
- [x] Deep link (`startapp` — Mini App; `?start=` — bot) qo'llab-quvvatlash
- [x] Erkin matn va mediaga muloyim javob
- [x] Webhook rejimi (prod), long polling (dev) — `BOT_MODE`
- [x] Kanal post shabloni (§8.4) — 1-bosqichda bajarildi
- [x] `/mening_malumotlarim` — eksport va o'chirish

### 3-bosqich — Qidiruv
- [ ] `GET /posts` to'liq filtrlar + keyset pagination
- [ ] `tsvector` matn qidiruvi + trigger
- [ ] Mini App qidiruv ekrani + filtr paneli + natija kartalari
- [ ] `/post/:id` tafsilot + "Bog'lanish" (reveal)
- [ ] Har bir qidiruv `search_queries`ga yoziladi
- [ ] Natijasiz qidiruvda obuna taklifi
- [ ] Saqlangan qidiruv → obuna

### 4-bosqich — Analitika va admin dashboard
- [ ] Metrikalar uchun SQL view'lar (`docs/METRICS.md`)
- [ ] Kunlik agregatlar (`daily_metrics`) + scheduled job
- [ ] Admin JWT auth + Telegram login
- [ ] Admin React skeleton + layout + jadval komponenti
- [ ] Overview, Posts (moderatsiya), Users, Reports sahifalari
- [ ] Analytics: voronka, kogorta, narx indeksi, talab/taklif
- [ ] Search Insights sahifasi
- [ ] Settings (feature flag) + Audit log

### 5-bosqich — Xabarnoma va ishonch
- [ ] `NotificationService` + mos e'lonni topish
- [ ] Anti-spam (kuniga 5 ta, birlashtirish)
- [ ] "Topildimi?" so'rovi (publish + 3 kun)
- [ ] Muddat tugashi ogohlantirishi
- [ ] Reytting va sharhlar
- [ ] `trust_score` hisoblash
- [ ] Shikoyat oqimi to'liq ishlaydi

### 6-bosqich (keyinroq, moliyaviy model tasdiqlagandan keyin)
- [ ] VIP e'lon (Telegram Stars)
- [ ] Tasdiqlash (verification) oqimi
- [ ] Ko'p koridor (`corridors` jadvali allaqachon tayyor)

---

## 14. KOD KONVENSIYALARI

**Java**
- Package: `uz.pochtajp.*`
- Entity — faqat domain, biznes mantiq `Service`da
- Controller ↔ Service ↔ Repository; Controller'da mantiq yo'q
- DTO'lar `record`, `@Valid` bilan
- Entity hech qachon API'dan qaytarilmaydi — faqat DTO
- MapStruct yoki qo'lda mapper (bir xil uslub tanla, aralashtirma)
- `Optional` qaytar, `null` emas
- Konstruktor injection (`@Autowired` field'da emas)

**TypeScript**
- `any` taqiqlangan
- API turlari `src/api/types.ts` da, backend DTO bilan bir xil
- TanStack Query — server holati; Zustand — faqat UI holati
- Komponent 200 qatordan oshsa bo'linadi
- CSS: CSS Modules yoki Tailwind — bittasini tanla, aralashtirma

**Git**
- Conventional commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `test:`
- Har bir bosqich alohida branch
- Migratsiya fayli hech qachon o'zgartirilmaydi — yangisi qo'shiladi

**Test**
- Har bir service metodiga unit test
- Har bir endpoint'ga MockMvc integratsiya testi (Testcontainers postgres bilan)
- `TelegramInitDataValidator` uchun alohida test: to'g'ri imzo, buzilgan imzo, eskirgan `auth_date`
- Qidiruv filtrlariga test
- Mini App: kritik oqimga Vitest + Testing Library

---

## 15. NIMA QILINMAYDI (anti-goals)

- ❌ Botda forma to'ldirish qaytarilmaydi
- ❌ Escrow, to'lov ushlab turish — bu bosqichda yo'q (litsenziya masalasi)
- ❌ Mobil ilova (iOS/Android) — Mini App yetarli
- ❌ Mikroservis arxitektura — bitta monolit yetarli va to'g'ri
- ❌ Kubernetes — Docker Compose yetarli
- ❌ Foydalanuvchi rasm yuklashi — hozircha yo'q (moderatsiya yuki, saqlash xarajati)
- ❌ Ichki chat — Telegram allaqachon bor
- ❌ AI/LLM funksiyalari — hozircha kerak emas
- ❌ Ma'lumotni hard delete qilish — hech qachon

---

## 16. TAYYORLIK MEZONLARI (Definition of Done)

Vazifa faqat quyidagilarning **hammasi** bajarilganda tugagan hisoblanadi:

1. Kod yozilgan va kompilyatsiya bo'ladi
2. Migratsiya yozilgan (agar sxema o'zgargan bo'lsa) va `validate` o'tadi
3. Test yozilgan va yashil
4. Tegishli event'lar yoziladi (agar foydalanuvchi harakati bo'lsa)
5. Xatolik holatlari ishlangan (network, validation, 401, 429)
6. UI matnlari 3 tilda (uz-latn, uz-cyrl, ru)
7. Log'da PII yo'q
8. `docs/EVENTS.md` yoki `docs/METRICS.md` yangilangan (agar kerak bo'lsa)
9. `.env.example` yangilangan (agar yangi o'zgaruvchi qo'shilgan bo'lsa)

---

## 17. CLAUDE CODE UCHUN ISH TARTIBI

1. **Har bir sessiya boshida** shu faylni o'qi va joriy bosqichni aniqla (§13 dagi belgilanmagan birinchi punkt).
2. **Bitta vazifani oxirigacha bajar** — yarim ishlangan holatda keyingisiga o'tma.
3. **Katta o'zgarishdan oldin rejani ayt** va tasdiqlashni kut.
4. **Migratsiya yozganingda** — nima o'zgarayotganini va nima uchun ekanini tushuntir.
5. **Sir yoki shaxsiy ma'lumot bilan ishlaganingda** — to'xta va ogohlantir.
6. **Shubha bo'lsa** — §1 dagi Mutlaq qoidalarga qayt. Ular hamma narsadan ustun.
7. **Bosqich tugagach** — §13 dagi katakchalarni belgila va qisqacha hisobot ber: nima qilindi, nima qoldi, keyingi qadam nima.
```
