# Pochta UZ ↔ JP

Yaponiya ↔ O'zbekiston yo'nalishida **pochta yuborish va olib ketish** e'lonlari uchun
marketplace: Telegram bot + Mini App + admin dashboard.

Loyihaning barcha qoidalari, ma'lumot modeli va bosqichlari — [`CLAUDE.md`](CLAUDE.md).
Uni o'qimasdan kod yozilmaydi.

| Qism | Papka | Holat |
|---|---|---|
| Backend (Spring Boot 3.3, Java 17) | `backend/` | 0-bosqich tugadi |
| Mini App (React + Vite + TS) | `miniapp/` | 1-bosqich |
| Admin dashboard (React + Vite + TS) | `admin/` | 4-bosqich |
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

## 3. Testlar

```bash
cd backend
./mvnw test
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

## 5. API

To'liq ro'yxat — `CLAUDE.md` §12. Hozir mavjud:

| Endpoint | Auth | Tavsif |
|---|---|---|
| `GET /health` | yo'q | salomatlik tekshiruvi |
| `POST /api/miniapp/events` | `Authorization: tma <initData>` | analitika event batch'i |

Mini App'ning **har bir** so'rovi `initData` HMAC imzosi bilan tekshiriladi (§7.1).
`user_id` request body'dan olinmaydi — faqat imzolangan `initData`dan.

## 6. Konvensiyalar

- Kod English, izohlar va UI matnlari o'zbekcha.
- Conventional commits: `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `test:`.
- Migratsiya fayli **hech qachon o'zgartirilmaydi** — yangisi qo'shiladi.
- `DELETE FROM` yozilmaydi — faqat `deleted_at = now()` (§1.1).
- `e.printStackTrace()` yo'q — SLF4J (§1.8).
