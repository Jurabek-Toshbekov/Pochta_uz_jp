# Reliz: Mini App — Netlify, backend — VPS

Ikkita alohida origin: statik ilova Netlify'da, backend o'z serverda.
Shuning uchun CORS jonlanadi va backend **albatta HTTPS** bo'lishi shart.

> **Nima uchun backendga ham HTTPS kerak.** Netlify sahifasi HTTPS'da
> ochiladi; undan `http://<IP>:8080` ga so'rov yuborilsa brauzer buni
> "mixed content" deb bloklaydi. Telegram webhook ham faqat haqiqiy
> sertifikatli HTTPS'ni qabul qiladi.

Amaldagi manzillar (2026-09-02 holati):

| Nima | Qayerda |
|---|---|
| Backend API | `https://api99.e-tex.uz` (VPS `169.58.233.232`) |
| Mini App | Netlify |
| Kanal | `@jpuzbpochta` (`-1001424117981`) |
| Bot | `@uzb_jp_elon_bot` |

**Diqqat:** bu serverda boshqa jonli loyiha ham bor — BookShare
(`api.e-tex.uz`, .NET, port 9000). nginx, PostgreSQL va Redis undan
qolgan. Shuning uchun quyidagi tartib "toza server" emas, "mavjud
serverga qo'shilish" tartibi.

---

## 1. Nima allaqachon bor edi

`nginx` (80/443 ni egallagan), `PostgreSQL 16`, `certbot`, `git`, `redis`.
Yo'q edi: **Java**.

```bash
apt-get install -y openjdk-17-jdk-headless   # JDK — serverda build qilamiz
```

> Caddy **ishlatilmadi**: nginx allaqachon 80/443 da turadi, Caddy
> ko'tarilmaydi va uni majburlash BookShare'ni o'chirib qo'yadi.

## 2. Baza

Mavjud PostgreSQL ichida alohida baza va foydalanuvchi. `bookshare`
bazalariga tegilmaydi.

```bash
sudo -u postgres psql -c "CREATE USER pochta WITH PASSWORD '<generatsiya>';"
sudo -u postgres createdb -O pochta pochta
sudo -u postgres psql -d pochta -c "CREATE EXTENSION IF NOT EXISTS unaccent;"
sudo -u postgres psql -d pochta -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;"
```

`unaccent` V4 migratsiyasi uchun shart va superuser talab qiladi.

## 3. Kod va build (serverda)

```bash
useradd --system --create-home --home-dir /opt/pochta --shell /usr/sbin/nologin pochta
mkdir -p /opt/pochta /etc/pochta && chown pochta:pochta /opt/pochta

sudo -u pochta git clone --branch main \
    https://github.com/Jurabek-Toshbekov/Pochta_uz_jp.git /opt/pochta/src

cd /opt/pochta/src/backend
sudo -u pochta HOME=/opt/pochta ./mvnw -B -DskipTests clean package
install -o pochta -g pochta -m 640 target/pochta-backend-*.jar /opt/pochta/pochta-backend.jar
```

Testlar serverda o'tkazilmaydi: Testcontainers Docker talab qiladi, Docker
esa bu serverda yo'q. Testlar lokal mashinada va CI'da ishlaydi.

### Yo'lda chiqqan ikkita nosozlik

**`./mvnw: command not found`** — repo Windows'da yaratilgani uchun git
`mvnw` ga exec-bit yozmagan edi. Repoda tuzatildi
(`git update-index --chmod=+x`), ya'ni yangi klonlarda muammo yo'q. Eski
klonlarda `sh ./mvnw` bilan ishga tushirish kifoya.

**`git fetch` → "could not read Username for https://github.com"** —
repo ochiq bo'lsa ham. Sabab: git 2.43 shu serverda HTTPS/2 orqali
GitHub bilan kelisha olmaydi (`curl` esa 200 qaytaradi). Yechim:

```bash
git config --system http.version HTTP/1.1
```

## 4. Sirlar

`/etc/pochta/pochta.env`, egasi root, huquq **600**. Kalitlar:

```
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1     # faqat localhost — nginx oldida turadi
SERVER_PORT=8080

BOT_TOKEN=...
BOT_USERNAME=uzb_jp_elon_bot
BOT_MINIAPP_SHORT_NAME=app

CHANNEL_CHAT_ID=-1001424117981
CHANNEL_USERNAME=jpuzbpochta

MINIAPP_URL=https://<netlify-manzil>    # CORS ro'yxatiga aynan shu tushadi
BOT_MODE=webhook
WEBHOOK_BASE_URL=https://api99.e-tex.uz
WEBHOOK_SECRET_TOKEN=<openssl rand -hex 32>

DB_URL=jdbc:postgresql://localhost:5432/pochta
DB_USERNAME=pochta
DB_PASSWORD=<generatsiya>

ADMIN_JWT_SECRET=<openssl rand -base64 48>
ADMIN_TELEGRAM_IDS=285855855
ADMIN_URL=

INITDATA_MAX_AGE_SECONDS=86400
RATE_LIMIT_POSTS_PER_DAY=5
RATE_LIMIT_REQUESTS_PER_MINUTE=60
LOG_LEVEL_APP=INFO
```

`MINIAPP_URL` o'zgarsa servisni restart qilish kerak — aks holda Mini App
CORS'da 403 ola boshlaydi.

## 5. nginx va sertifikat

```bash
cp deploy/nginx-pochta.conf /etc/nginx/sites-available/pochta
ln -sfn /etc/nginx/sites-available/pochta /etc/nginx/sites-enabled/pochta
nginx -t && systemctl reload nginx          # uzilishsiz, BookShare tirik qoladi

certbot --nginx -d api99.e-tex.uz --non-interactive --agree-tos --redirect
```

Certbot 443 blokini va HTTP→HTTPS yo'naltirishni o'zi qo'shadi, yangilanish
uchun cron/timer ham o'zi sozlaydi.

## 6. Servis

```bash
cp deploy/pochta-backend.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pochta-backend
journalctl -u pochta-backend -f
```

Webhook `prod` profilida ilova ko'tarilganda `BotWebhookRegistrar`
tomonidan o'zi ro'yxatdan o'tadi.

## 7. Netlify

`netlify.toml` repo ildizida (`base = "miniapp"`, `publish = "dist"`,
Node 22). Netlify UI'da repo ulanadi, so'ng bitta env qo'shiladi:

```
VITE_API_BASE_URL = https://api99.e-tex.uz
```

`HashRouter` ishlatilgani uchun SPA rewrite qoidasi kerak emas.
`X-Frame-Options` **qo'yilmaydi** — Mini App Telegram webview ichida
ochiladi va u sarlavha ilovani butunlay bloklaydi.

## 8. BotFather

`/newapp` → `@uzb_jp_elon_bot` → rasm 640×360, GIF uchun `/empty`,
Web App URL = Netlify manzili, **Short name: `app`**.

Shundan keyin kanaldagi `t.me/uzb_jp_elon_bot/app?startapp=ch_<id>`
havolasi ishlaydi.

---

## Tekshiruv

```bash
curl https://api99.e-tex.uz/health                      # {"status":"UP"}
curl -o /dev/null -w '%{http_code}\n' \
     https://api99.e-tex.uz/api/miniapp/reference        # 401 — initData yo'q, to'g'ri
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

8080 tashqaridan yopiq bo'lishi kerak (`SERVER_ADDRESS=127.0.0.1`).

## Tartib muhim

Kanalga chiqqan post **abadiy qoladi**, undagi havola BotFather'dagi Web
App URL'iga bog'langan:

1. Netlify manzili yakunlansin
2. BotFather'ga o'sha manzil yozilsin
3. `.env` dagi `MINIAPP_URL` ham o'sha bo'lsin
4. **Faqat shundan keyin** haqiqiy kanalga e'lon chiqarilsin

Aks holda birinchi postlarning "Bog'lanish" havolasi keyin buziladi va
uni orqaga tuzatib bo'lmaydi.

## Yangilash

```bash
cd /opt/pochta/src && sudo -u pochta git pull
cd backend && sudo -u pochta HOME=/opt/pochta ./mvnw -B -DskipTests clean package
sudo install -o pochta -g pochta -m 640 target/pochta-backend-*.jar /opt/pochta/pochta-backend.jar
sudo systemctl restart pochta-backend
```

Migratsiyalar Flyway tomonidan ko'tarilishda qo'llanadi
(`ddl-auto=validate`, §1.3) — qo'lda SQL ishlatilmaydi.

Mini App: Netlify repo'ga push bo'lganda o'zi qayta quradi.
