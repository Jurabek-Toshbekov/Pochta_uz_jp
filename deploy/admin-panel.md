# Admin panelni serverga chiqarish rejasi

> Server **BookShare** loyihasi bilan bo'lishilgan. Butun reja bitta shartga
> bo'ysunadi: **ishlab turgan hech narsaga tegilmaydi.** Har qadamdan keyin
> ikkala loyiha ham tekshiriladi.

---

## 1. Tanlangan yechim va nima uchun

Panel **VPS'da turadi, lekin internetda ko'rinmaydi** — kirish faqat SSH
tunnel orqali, GoAccess bilan bir xil usulda.

```
SIZNING PC                      VPS
──────────                      ──────────────────────────────────
brauzer                         nginx (yangi blok, 127.0.0.1:8082)
  localhost:8082  ══SSH══►        ├── /      → /var/www/pochta-admin
                                  └── /api/* → 127.0.0.1:8080
```

Uch xil yo'l bor edi; bu tanlanganining sababi:

| | SSH tunnel | Netlify ochiq URL | VPS + subdomen |
|---|---|---|---|
| Internetda ko'rinadi | **yo'q** | ha | ha |
| Backend restart | **yo'q** | yo'q | yo'q |
| CORS o'zgarishi | **yo'q** | yo'q | yo'q |
| DNS / sertifikat | **kerak emas** | Netlify beradi | kerak |
| nginx'ga tegish | **+1 yangi fayl** | umuman yo'q | +1 fayl, lekin certbot umumiy konfigga tegadi |
| Telefondan kirish | yo'q | ha | ha |

Admin panel — eng nozik yuza (moderatsiya, foydalanuvchi bloklash,
analitika). Uni ochiq internetda ushlab turmaslik, telefondan kirish
qulayligidan qimmatroq.

**CORS nima uchun kerak emas:** brauzer uchun sahifa ham, `/api/**` ham
bitta origin (`localhost:8082`) — nginx `/api` ni ichkariga uzatadi.
Shuning uchun `ADMIN_URL` ni sozlash va **backendni qayta ishga tushirish
shart emas.** Bu muhim: restart bot webhook'ini va Mini App'ni bir necha
soniya uzib turadi.

## 2. Portlar

| Port | Nima | Bog'lanish |
|---|---|---|
| 80, 443 | system nginx — **tegilmaydi** | `0.0.0.0` |
| 7890, 7891 | GoAccess | `127.0.0.1` |
| **8082** | admin panel | **`127.0.0.1`** |
| 8080 | Pochta backend | `127.0.0.1` |
| 9000 | BookShare | `127.0.0.1` |

## 3. Qadamlar

Har qadam tekshiruv bilan tugaydi. Yiqilsa — keyingisiga o'tilmaydi.

### 3.1 — Baseline
Ikkala sayt, `nginx -t`, portlar ro'yxati yozib olinadi.

### 3.2 — Lokalda build
`admin/` ichida `npm run build`. `VITE_API_BASE_URL` **berilmaydi** — bo'sh
qolsa klient nisbiy `/api/admin/...` yo'lini ishlatadi, aynan shu kerak
(§1: bir xil origin).

**Tekshiruv:** `tsc -b` xatosiz o'tadi, `dist/` yaratiladi.

### 3.3 — Fayllarni serverga yuklash
`/var/www/pochta-admin/` ga `scp` bilan. Eski nusxa bo'lsa almashtiriladi.

**Tekshiruv:** `index.html` va `assets/` joyida, hajmi mos.

### 3.4 — nginx bloki
`/etc/nginx/conf.d/admin-panel.conf` — **yangi** fayl. Ichida:
- `listen 127.0.0.1:8082;` — tashqaridan ko'rinmaydi
- statik fayllar + `try_files $uri /index.html` (BrowserRouter uchun shart,
  aks holda `/posts` ni yangilaganda 404 chiqadi)
- `location /api/ { proxy_pass http://127.0.0.1:8080; }`
- `X-Robots-Tag: noindex` va xavfsizlik sarlavhalari

`nginx -t` muvaffaqiyatli bo'lsagina `systemctl reload nginx`.

**Tekshiruv:** serverdan `curl 127.0.0.1:8082` → 200; `/api/admin/overview`
→ 401 (backendga yetib borgani isboti); tashqaridan 8082 → yopiq.

### 3.5 — Tunnel
`~/.ssh/config` ga `admin` hosti (`LocalForward 8082`).

**Tekshiruv:** brauzerda sahifa ochiladi, kirish oynasi chiqadi.

### 3.6 — Haqiqiy kirish sinovi
Botdagi `/admin` buyrug'i bilan kod olinadi va panelga kiriladi. Overview
sahifasidagi raqamlar jonli bazadan kelayotgani tasdiqlanadi.

### 3.7 — Regressiya
BookShare, Pochta backend, Mini App, bot webhook, `nginx -t`,
`sites-enabled`, mavjud nginx fayllarining `diff`i.

## 4. Nimaga tegilmaydi

- ❌ `/etc/nginx/nginx.conf`
- ❌ `/etc/nginx/sites-*/bookshare`, `/etc/nginx/sites-*/pochta`
- ❌ 80/443 portlari
- ❌ `/etc/pochta/pochta.env` — `ADMIN_URL` sozlanmaydi
- ❌ **`pochta-backend` servisi qayta ishga tushirilmaydi**
- ❌ BookShare servisi, bazasi, Redis, sertifikatlari
- ❌ ufw

## 5. Yangilash (keyingi safar)

```bash
cd admin && npm run build
scp -r dist/* pochta:/var/www/pochta-admin/
```

nginx reload ham kerak emas — statik fayllar almashadi, xolos.

## 6. Orqaga qaytarish

```bash
rm -f /etc/nginx/conf.d/admin-panel.conf
nginx -t && systemctl reload nginx
rm -rf /var/www/pochta-admin
```

Mavjud fayllar tahrirlanmagani uchun bu boshlang'ich holatga to'liq
qaytaradi. Baseline zaxirasi: `/root/backup-before-npm/`.

---

# Bajarilish hisoboti — 2026-09-03

## Kirish

```bash
ssh admin
# brauzerda: http://localhost:8082
```

Kod botdan olinadi: **@uzb_jp_elon_bot** ga `/admin` → 8 belgili kod
(5 daqiqa yashaydi, bir marta ishlaydi). Telegram ID `285855855` prod
`ADMIN_TELEGRAM_IDS` da bor, rol `ADMIN`.

## Natija

| Qadam | Holat |
|---|---|
| 3.1 Baseline | ✅ |
| 3.2 Build | ✅ (avval buzuq edi — tuzatildi, quyida) |
| 3.3 Yuklash | ✅ 648 KB, `www-data:www-data` |
| 3.4 nginx bloki | ✅ (bir marta 403 bilan yiqildi — tuzatildi) |
| 3.5 Tunnel | ✅ |
| 3.6 Haqiqiy kirish | ✅ brauzerda skrinshot bilan tasdiqlandi |
| 3.7 Regressiya | ✅ |

Jonli ma'lumot panelda: 2 e'lon bugun, DAU 22, publish konversiyasi 43.8%,
fill rate 66.7%, natijasiz qidiruv 20%, time-to-publish 2 daq 41 s,
voronka 16 → 8 → 7 → 7. Moderatsiya, foydalanuvchilar (42 ta) va qidiruv
tahlili sahifalari ham ma'lumot qaytardi.

## Regressiya

| Nima | Baseline | Keyin |
|---|---|---|
| `api.e-tex.uz` (BookShare) | 404 | **404** |
| `api99.e-tex.uz/health` | 200 | **200** |
| Netlify Mini App | 200 | **200** |
| `nginx -t` | ok | **ok** |
| `sites-enabled` | bookshare, pochta | **o'zgarmagan** |
| `nginx.conf` | — | **o'zgarmagan** |
| `sites-available/bookshare` | — | **o'zgarmagan** |
| `sites-available/pochta` | — | **o'zgarmagan** |
| **`pochta-backend` start vaqti** | 2026-09-02 15:15 | **2026-09-02 15:15 — restart bo'lmagan** |
| Bot webhook | ishlayapti | **navbat 0** |
| Tashqi 8082 | — | **yopiq** (000) |

## Ikkita tuzatilgan nuqson

### 1. Admin build avvaldan buzuq edi

`npm run build` (`tsc -b && vite build`) ikkita xato bilan yiqilardi va bu
`dba3330` dan beri shunday edi — panel hech qachon prod uchun qurilmagan:

- `authFlow.test.ts` `tokenStore.save()` ga faqat ikkita token beradi,
  imzo esa to'liq `LoginResponse` talab qilardi. Yechim: imzo
  `Pick<LoginResponse, 'accessToken' | 'refreshToken'>` ga toraytirildi —
  `save` boshqa maydonlarni ishlatmaydi (rol va muddat har so'rovda
  bazadan qayta tekshiriladi).
- `vite.config.ts` da `process.cwd()` — `@types/node` yo'q. `loadEnv` ga
  envDir sifatida `'.'` berildi.

22 test yashil, build 2.57 s.

### 2. Brauzerdan 403, curl'dan 401

Kirish tugmasi bosilganda 403 kelardi, `curl` bilan esa hammasi ishlardi.
Farq **`Origin` sarlavhasida**: brauzer POST so'rovda uni yuboradi, curl
yubormaydi.

Birinchi gumon `proxy_set_header Host $host` bo'ldi — `$host` port raqamini
tashlab yuboradi, backend `Host: localhost` ko'radi. Tuzatildi
(`$http_host`), lekin **403 qolib ketdi**.

Haqiqiy sabab chuqurroq: **Spring 5.3+ da `CorsUtils.isCorsRequest()`
faqat `Origin` sarlavhasi bor-yo'qligini tekshiradi** — bir xil origin
bo'lsa ham so'rov CORS deb belgilanadi. Ruxsat ro'yxatida esa faqat
`MINIAPP_URL` turadi (`ADMIN_URL` bo'sh), shuning uchun `localhost:8082`
dan kelgan har bir POST 403 olardi.

Yechim: nginx `Origin` ni **olib tashlaydi** (`proxy_set_header Origin "";`).
Loyihaning Vite dev proxy'lari ham aynan shu usulni ishlatadi
(`admin/vite.config.ts`, `miniapp/vite.config.ts`).

**Xavfsizlikka ta'siri yo'q:** admin API cookie emas, `localStorage`dagi
Bearer token bilan ishlaydi — begona sahifa tokenni o'qiy ham, so'rovga
qo'shdira ham olmaydi, ya'ni CSRF vektori mavjud emas. Muqobil yechim
(`ADMIN_URL` ni sozlash) backend restartini talab qilardi.

> Bu nuqson faqat **haqiqiy brauzerda** ko'rindi. `curl` bilan cheklanib
> qolsak, panel "ishlaydi" deb topshirilar va foydalanuvchi kirishga
> uringanda yiqilardi.

## Sinov kodlari

Kirishni oxirigacha sinash uchun prod bazasiga bir martalik kodlar
qo'yildi (bot `/admin` bilan aynan shu narsani qiladi). Sinovdan keyin
ochiq qolganlari `deleted_at = now()` bilan yopildi (§1.1 — hard delete
yo'q). Ochiq kod qolmadi.

## Fayllar

| Fayl | Holat |
|---|---|
| `/etc/nginx/conf.d/admin-panel.conf` | **yangi** |
| `/var/www/pochta-admin/` | **yangi** (648 KB) |
| `~/.ssh/config` → `Host admin` | **yangi** |
| `/etc/nginx/nginx.conf`, `bookshare`, `pochta` | **o'zgarmagan** |
| `/etc/pochta/pochta.env` | **o'zgarmagan** (`ADMIN_URL` sozlanmadi) |
