# Pochta UZ ↔ JP

Yaponiya ↔ O'zbekiston yo'nalishida **pochta yuborish va olib ketish** e'lonlari uchun
marketplace: Telegram bot + Mini App + admin dashboard.

Loyihaning barcha qoidalari, ma'lumot modeli va bosqichlari — [`CLAUDE.md`](CLAUDE.md).
Uni o'qimasdan kod yozilmaydi.

| Qism | Papka | Holat |
|---|---|---|
| Backend + bot (Spring Boot 3.3, Java 17) | `backend/` | 0–5-bosqich tugadi |
| Mini App (React + Vite + TS) | `miniapp/` | 1-, 3- va 5-bosqich tugadi |
| Admin dashboard (React + Vite + TS) | `admin/` | 4-bosqich tugadi |
| Hujjatlar | `docs/` | [EVENTS.md](docs/EVENTS.md), [METRICS.md](docs/METRICS.md) |

---

## 1. Talablar

- JDK 17
- PostgreSQL 16 (yoki Docker)
- Docker Desktop — integratsiya testlari (Testcontainers) uchun

## 2. Sozlash

Sirlar **hech qachon kodda bo'lmaydi** (`CLAUDE.md` §1.2). Hammasi `.env` faylida:

```bash
cp .env.example .env
# .env ni to'ldiring: BOT_TOKEN, DB_PASSWORD, ADMIN_JWT_SECRET
```

`.env` `.gitignore`da — commit qilinmaydi.

### Docker bilan (tavsiya etiladi)

```bash
docker compose up -d          # postgres + backend
docker compose logs -f backend
curl http://localhost:8080/health
```

### Lokal (o'z Postgres'ingiz bilan)

```bash
createdb pochta
cd backend
DB_URL=jdbc:postgresql://localhost:5432/pochta \
DB_USERNAME=postgres DB_PASSWORD=... BOT_TOKEN=... \
./mvnw spring-boot:run
```

Flyway migratsiyalarni o'zi qo'llaydi. `ddl-auto=validate` — Hibernate sxemani
**o'zgartirmaydi**, faqat tekshiradi (§1.3).

**Diqqat:** `V4` migratsiyasi matn qidiruvi uchun `unaccent` extension'ini
yaratadi va bu bir marta superuser huquqini talab qiladi. DB foydalanuvchisi
superuser bo'lmasa, migratsiyadan oldin bir marta qo'lda bajaring:

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
```

### Mini App

```bash
cd miniapp
cp .env.example .env          # VITE_API_BASE_URL ni backend manzilingizga qo'ying
npm install
npm run dev                   # http://localhost:5173
```

Backend `localhost:8080` da bo'lsa, alohida sozlash kerak emas: `VITE_API_BASE_URL`
bo'sh qoldiriladi va Vite dev proxy `/api`, `/health`, `/webhook` so'rovlarini
backendga uzatadi. So'rov same-origin bo'lgani uchun CORS ham kerak emas.

Telegram tashqarisida brauzerda ochilsa `initData` bo'lmaydi va API 401 qaytaradi —
bu kutilgan holat (§7.1). UI'ni brauzerda ko'rish uchun `VITE_DEV_INIT_DATA`
(pastda), Telegramda ko'rish uchun tunnel kerak.

### Tunnel orqali Telegramda sinash

Telegram Mini App URL'i HTTPS bo'lishi shart, shuning uchun tunnel kerak.
Vite tashqi xostni bloklaydi (DNS rebinding himoyasi) — keng tarqalgan tunnel
domenlari `vite.config.ts` dagi `server.allowedHosts` da ruxsat etilgan.

**Faqat bitta tunnel kerak** — dev proxy backendni ham shu domen ostiga olib
chiqadi:

```bash
cloudflared tunnel --url http://localhost:5173     # yoki: ngrok http 5173
```

Keyin @BotFather → bot → *Bot Settings* → *Menu Button* / *Mini App* →
tunnel URL'ini qo'yish.

**ngrok bepul rejasi Telegram uchun ishlamaydi.** Brauzerdan kelgan HTML
so'rovga "You are about to visit…" sahifasi qaytadi (`ERR_NGROK_6024`) va
Telegram webview'i Mini App o'rniga aynan shuni ko'rsatadi. Traffic Policy
bilan `ngrok-skip-browser-warning` headerini qo'shish ham yordam bermaydi —
ogohlantirish policy'dan oldin qo'llanadi (sinab ko'rilgan:
`scripts/ngrok-skip-warning.yml`). Yechim: `cloudflared` yoki pullik ngrok.

Tunnel URL'i `cloudflared`da har ishga tushirishda o'zgaradi, ya'ni
@BotFather'da qayta sozlash kerak bo'ladi.

```bash
npm run lint                  # tsc --noEmit
npm run test                  # Vitest
npm run build                 # dist/ ≈103 KB gzip (§9.5 chegarasi 200 KB)
```

## 3. Testlar

```bash
cd backend
./mvnw test                   # 145 test: unit + Testcontainers integratsiya

cd ../miniapp
npm run test                  # 44 test: forma mapping, qidiruv filtrlari, i18n, checklist
```

Integratsiya testlari Testcontainers orqali haqiqiy PostgreSQL 16 ko'taradi, ya'ni
Docker ishlab turishi kerak.

**Windows + Docker Desktop.** Testcontainers standart holatda `\\.\pipe\docker_engine`
quvurini izlaydi, Docker Desktop esa `desktop-linux` kontekstida ishlaydi. Agar
"Could not find a valid Docker environment" chiqsa, `~/.testcontainers.properties`
faylini yarating:

```properties
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

Docker Engine API versiyasi `pom.xml`da (`api.version=1.44`) sozlangan — Docker 29
eski API versiyalarini rad etadi, shu sabab kerak.

---

## 4. MUHIM: git tarixidagi sirni tozalash

Loyihaning birinchi commit'larida **bot token va DB paroli ochiq kodda** bo'lgan
(`CLAUDE.md` §2, 1- va 2-nuqsonlar). Ular kodning joriy holatidan olib tashlandi,
lekin **git tarixida qoladi**. Repo public bo'lsa yoki bo'lgan bo'lsa, token
allaqachon oshkor hisoblanadi.

### 4.1 Birinchi navbatda — tokenni bekor qilish

Tarixni tozalashdan **oldin** bu qadam bajarilishi shart, chunki tarixni
tozalash oshkor bo'lgan tokenni qaytarib olmaydi:

1. Telegramda [@BotFather](https://t.me/BotFather) → `/revoke` → botni tanlash.
2. Yangi tokenni **faqat** `.env` faylidagi `BOT_TOKEN`ga yozish.
3. DB parolini ham almashtirish:
   ```sql
   ALTER USER pochta WITH PASSWORD 'yangi-parol';
   ```
   va `.env`dagi `DB_PASSWORD`ni yangilash.

### 4.2 Tarixni tozalash

Tavsiya: [`git-filter-repo`](https://github.com/newren/git-filter-repo)
(BFG ham bo'ladi, `filter-branch` esa tavsiya etilmaydi).

```bash
pip install git-filter-repo

# 1. Zaxira nusxa — bu operatsiya barcha commit hash'larini o'zgartiradi
git clone --mirror . ../pochta-backup.git

# 2. Sirlarni almashtirish ro'yxati
cat > /tmp/secrets.txt <<'EOF'
7090852538:AAExxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx==>BOT_TOKEN_REDACTED
1111==>DB_PASSWORD_REDACTED
EOF

# 3. Tarixni qayta yozish
git filter-repo --replace-text /tmp/secrets.txt

# 4. Majburiy push (jamoa bilan kelishib!)
git push --force --all
git push --force --tags
```

**Ogohlantirish.** `--force` push barcha hash'larni o'zgartiradi: jamoadagi har
kim o'z klonini qayta olishi kerak (`git clone`), aks holda eski tarix qaytib
kelib qoladi. Fork'lar, PR'lar va CI kesh'larida eski commit'lar saqlanib
qolishi mumkin — shu sababli 4.1 qadami (tokenni bekor qilish) yagona
ishonchli himoya.

Agar repo GitHub'da bo'lsa, qo'shimcha ravishda Support'ga murojaat qilib
kesh'langan commit'larni tozalashni so'rash kerak.

---

## 5. Admin dashboard

Boshqaruv paneli `admin/` papkasida (React + Vite + TS, Recharts).

```bash
cd admin
npm install
npm run dev          # http://localhost:5174
```

Dev serverda `/api` so'rovlari Vite proxy orqali backendga uzatiladi — CORS
sozlash shart emas.

### Kirish

Parol yo'q. Telegram bot orqali (§11.1):

1. `.env` da `ADMIN_JWT_SECRET` to'ldirilgan bo'lsin. **Bo'sh bo'lsa admin API
   butunlay yopiq turadi** — bu ataylab.
2. `ADMIN_TELEGRAM_IDS` ga o'z `telegram_id` ingizni yozing. Bu ro'yxatdagilar
   birinchi kirishda avtomatik `ADMIN` rolini oladi.
3. Botga `/admin` deb yozing — u 8 belgili kod yuboradi.
4. Kodni panelga kiriting. Kod 5 daqiqa yashaydi va bir marta ishlaydi.

Kod bazada ochiq matnda saqlanmaydi — faqat SHA-256. Access token 2 soat,
refresh 14 kun. Rol **har bir so'rovda** bazadan qayta tekshiriladi: huquq
olib qo'yilsa token darhol ishlamay qoladi.

### Sahifalar

| Sahifa | Nima uchun |
|---|---|
| Umumiy ko'rinish | KPI kartalari, 30 kunlik e'lonlar, voronka |
| Moderatsiya | `PENDING` va yuqori riskli e'lonlar tepada; tasdiqlash / rad etish / yopish |
| Foydalanuvchilar | Profil, e'lonlar, event lentasi; bloklash va tasdiqlash |
| Shikoyatlar | Ochiq murojaatlar navbati |
| Analitika | Narx indeksi, talab/taklif, kogorta, match latency, mavsumiylik |
| Qidiruv tahlili | Natijasiz qidiruvlar va talab/taklif yonma-yon — eng qimmatli sahifa |
| Xabarnomalar | CTR va obunalar (yuborish 5-bosqichda) |
| Sozlamalar | Feature flag'lar. O'zgartirish faqat `ADMIN` roli uchun |
| Audit | Kim, nima qildi, qachon. Faqat o'qish |

### Feature flag'lar

`app_settings` jadvalida, panel orqali boshqariladi:

| Kalit | Ta'siri |
|---|---|
| `moderation.required` | Yoqilsa e'lon kanalga chiqmaydi, `PENDING` holatida admin tasdiqlashini kutadi |
| `vip.enabled` | 6-bosqich uchun tayyorlangan |
| `rate_limit.posts_per_day` | Kuniga e'lon chegarasi (env qiymatidan ustun) |
| `rate_limit.requests_per_minute` | API so'rovlari chegarasi |
| `notifications.max_per_day` | Anti-spam chegarasi (5-bosqich) |

### Kunlik agregatlar

`DailyMetricsJob` har kecha **03:15 UTC** da o'tgan kun metrikalarini
`daily_metrics` jadvaliga yozadi. Job idempotent — orqaga qarab to'ldirish
uchun ham shu metod ishlatiladi. Batafsil: [`docs/METRICS.md`](docs/METRICS.md).


## 6. Xabarnoma va ishonch

### Xabarnomalar

E'lon kanalga chiqqanda obunalar tekshiriladi va mos kelganlar **navbatga**
yoziladi. Har 2 daqiqada digest job navbatni foydalanuvchi kesimida guruhlab
yuboradi — bir vaqtda 5 ta e'lon chiqsa, odam 5 ta emas, **bitta** xabar oladi.

Uchta anti-spam chegarasi (§10.3):

| Chegara | Qanday ishlaydi |
|---|---|
| Takrorlanmaslik | `(post_id, user_id, kind)` unikal indeks — job qayta ishlasa ham ikkinchi xabar ketmaydi |
| Kunlik chegara | `notifications.max_per_day` (standart 5). Oshsa yozuv `BLOCKED` bo'ladi, o'chirilmaydi |
| Birlashtirish | Bir yuborishda bitta xabar, nechta e'lon bo'lsa ham |

Xabarnomadagi tugma `t.me/<bot>/app?startapp=nt_<postId>` havolasi — WebApp
tugmasi `startapp` ni uzatmaydi, shuning uchun aynan URL tugmasi. Ochilish
`notification_opened` sifatida yoziladi va CTR shundan hisoblanadi.

### Kunlik eslatmalar (`PostFollowUpJob`, 09:00 UTC)

- **"Odam topdingizmi?"** — publish'dan 3 kun keyin. Uchta javob:
  `Odam topildi` (e'lon yopiladi, bitim yoziladi), `Hali javob yo'q` (e'lon
  ochiq qoladi, javob event sifatida yoziladi), `Rejam o'zgardi` (yopiladi).
  Uchalasi alohida saqlanadi — ularni bitta "yopildi" ga qo'shish ma'lumotni yo'qotadi.
- **Muddat ogohlantirishi** — tugashiga 1 kun qolganda.

### Reytting va ishonch balli

Baho faqat bitimda qatnashgan odamdan qabul qilinadi: e'lon egasi sherigini,
kontakt ochgan odam e'lon egasini. Boshqa hech kim — aks holda reytting
soxtalashtirish quroliga aylanadi.

`trust_score` (0..100) to'rtta manbadan yig'iladi:

```
10 bazaviy + baho×10 (maks 50) + yakunlangan bitim×5 (maks 20)
+ tasdiqlanish (PHONE 10 / DOCUMENT 20) − ASOSLI shikoyat×10 (maks 40)
```

Faqat **hal qilingan** shikoyat ballni tushiradi: ochiq shikoyat hali
tekshirilmagan va u bilan jazolash raqobatchiga qurol berish bo'lardi.

### Shikoyat

Har bir e'londa "⚠️ Shikoyat qilish" tugmasi (§7.3). Shikoyat e'lonni
**yopmaydi** — u moderatsiya navbatiga tushadi va qarorni admin qabul qiladi.
Bitta odam bitta e'longa bir marta shikoyat qila oladi.


## 7. Bot

Bot **forma to'ldirmaydi**. Uning ishi: kutib olish, tushuntirish, Mini App'ga
yo'naltirish, xabar berish (§8).

| Buyruq | Nima qiladi |
|---|---|
| `/start` | Salomlashish + WebApp tugmalari |
| `/elon` | Mini App'ni e'lon berish ekranida ochadi |
| `/qidiruv` | Mini App qidiruv ekrani |
| `/mening_elonlarim` | Mini App "Mening e'lonlarim" |
| `/obuna` | Xabarnoma obunalari |
| `/xavfsizlik` | Taqiqlangan buyumlar va xavfsizlik qoidalari |
| `/qoidalar` | Qoidalar va foydalanish shartlari |
| `/til` | uz-latn / uz-cyrl / ru |
| `/yordam` | Ko'p so'raladigan savollar |
| `/mening_malumotlarim` | Ma'lumot eksporti (JSON) va o'chirish |
| `/admin` | Admin panelga kirish kodi. Menyuda ko'rinmaydi, faqat MODERATOR/ADMIN uchun |

### Rejimlar

`BOT_MODE` bilan boshqariladi:

- `polling` — lokal ishlab chiqish. Ishga tushishda webhook o'chiriladi.
- `webhook` — prod. `WEBHOOK_BASE_URL` va `WEBHOOK_SECRET_TOKEN` kerak.
  Telegram `POST /webhook/telegram/{secret}` ga yozadi; sir **yo'lda ham,
  `X-Telegram-Bot-Api-Secret-Token` headerida ham** tekshiriladi (§7.2).
- `off` — bot ishga tushmaydi (testlar, faqat API kerak bo'lgan holat).

Token yaroqsiz bo'lsa ilova **to'xtamaydi** — API va Mini App ishlashda davom
etadi, xato log'da qoladi.

### Ma'lumot o'chirish nima qiladi

`/mening_malumotlarim` → o'chirish tasdiqlangach:

- ism, familiya, username, telefon va e'lonlardagi kontaktlar tozalanadi
- `users.deleted_at` qo'yiladi — **qator o'chirilmaydi** (§1.1)
- faol e'lonlar `CLOSED` bo'ladi, obunalar o'chadi
- event'lar va analitika joyida qoladi — ularda PII yo'q (§1.7)
- `audit_log`ga yozib qo'yiladi

## 8. API

To'liq ro'yxat — `CLAUDE.md` §12. Hozir mavjud:

| Endpoint | Auth | Tavsif |
|---|---|---|
| `GET /health` | yo'q | salomatlik tekshiruvi |
| `POST /api/miniapp/session` | `Authorization: tma <initData>` | profil, ToS/Privacy roziligi, til, `start_param` |
| `GET /api/miniapp/reference` | initData | aeroport, kategoriya, koridor (1 soat kesh) |
| `POST /api/miniapp/posts` | initData | e'lon yaratish va kanalga chiqarish |
| `GET /api/miniapp/posts` | initData | qidiruv: filtrlar + keyset pagination |
| `GET /api/miniapp/posts/{id}` | initData | e'lon tafsiloti — **kontaktsiz** |
| `POST /api/miniapp/posts/{id}/reveal-contact` | initData | kontaktni ochadi va yozib qo'yadi |
| `GET /api/miniapp/my/posts[/{id}]` | initData | faqat o'z e'lonlari |
| `GET/POST/DELETE /api/miniapp/subscriptions` | initData | xabarnoma obunalari |
| `GET/PUT/DELETE /api/miniapp/drafts` | initData | forma autosave |
| `POST /api/miniapp/events` | initData | analitika event batch'i |

Qidiruv `?type=&direction=&origin=&dest=&dateFrom=&dateTo=&categories=&priceMax=`
`&currency=&verifiedOnly=&q=&sort=&cursor=&size=` parametrlarini oladi.
Sahifalash **keyset** (offset emas): javobdagi `nextCursor` keyingi so'rovga
beriladi. `totalCount` faqat birinchi sahifada keladi.

Mini App'ning **har bir** so'rovi `initData` HMAC imzosi bilan tekshiriladi (§7.1).
`user_id` request body'dan olinmaydi — faqat imzolangan `initData`dan.

## 9. Konvensiyalar

- Kod English, izohlar va UI matnlari o'zbekcha.
- Conventional commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `test:`.
- Migratsiya fayli **hech qachon o'zgartirilmaydi** — yangisi qo'shiladi.
- `DELETE FROM` yozilmaydi — faqat `deleted_at = now()` (§1.1).
- `e.printStackTrace()` yo'q — SLF4J (§1.8).
