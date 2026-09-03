# GoAccess — server statistikasi

> Server **BookShare** loyihasi bilan bo'lishilgan. Butun yechim bitta shartga
> bo'ysunadi: **ishlab turgan hech narsaga tegilmaydi.** Mavjud nginx
> fayllarining birortasi ham o'zgartirilmagan — faqat bitta yangi fayl
> qo'shilgan.

---

## 1. Nima bu va nima emas

GoAccess nginx access log'ini o'qib jonli HTML hisobot beradi. Bepul,
ochiq kodli, bitta C dasturi, tashqi xizmatga hech narsa yubormaydi.

**Bu admin dashboard'ni almashtirmaydi** — ikkalasi ikki xil savolga javob
beradi:

| | GoAccess | Admin dashboard |
|---|---|---|
| Manba | nginx log | PostgreSQL + event'lar |
| Ko'rsatadi | HTTP trafik: IP, brauzer, javob vaqti, 404, bot/spider, hajm | E'lonlar, voronka, qidiruv, kontakt ochilishi, narx indeksi |
| Savol | "Server qanday ishlayapti?" | "Mahsulot ishlayaptimi?" |

> Avval bu ish uchun Nginx Proxy Manager o'rnatilgandi — u proxy
> boshqaruvchisi, analitika asbobi emas. Olib tashlandi, Docker bilan birga.

## 2. Asosiy muammo va yechim

Serverdagi boshlang'ich holat:

```
/etc/nginx/nginx.conf:40   access_log /var/log/nginx/access.log;
```

Global sozlama — ikkala loyiha bitta faylga yozadi, format esa sukut
`combined`, unda **domen nomi ham, javob vaqti ham yo'q**:

```
122.26.124.5 - - [03/Sep/2026:07:22:57 +0200] "POST /api/miniapp/events HTTP/1.1" 202 0 "..." "..."
                                               ^ qaysi domen? qancha vaqt ketdi? — yozilmagan
```

Bunday logdan na loyihalarni ajratib bo'ladi, na tezlikni o'lchab.

**Yechim: nginx bir vaqtda bir necha faylga yoza oladi.** Eski `access.log`
o'z formati va mazmuni bilan **o'zgarishsiz** qolaveradi (BookShare uchun
hech narsa o'zgarmaydi), yoniga esa yangi fayl qo'shiladi:

```nginx
# /etc/nginx/conf.d/goaccess-log.conf  — YANGI fayl
log_format goaccess_ext '$remote_addr - $remote_user [$time_local] '
                        '"$host" "$request" $status $body_bytes_sent '
                        '"$http_referer" "$http_user_agent" $request_time';

access_log /var/log/nginx/all-access.log goaccess_ext;
```

`conf.d/*.conf` nginx.conf ning `http` blokidan include qilinadi
(`nginx.conf:59`), shuning uchun bu qator butun serverga tegishli bo'ladi
va **hech qaysi mavjud faylni tahrirlash shart emas.**

Natijada har bir so'rov ikkala faylga yoziladi:

```
access.log      1.2.3.4 - - [...] "POST /api/... HTTP/1.1" 202 0 "..." "..."
all-access.log  1.2.3.4 - - [...] "api99.e-tex.uz" "POST /api/... HTTP/1.1" 202 0 "..." "..." 0.011
                                   ^^^^^^^^^^^^^^^ domen             javob vaqti ^^^^^
```

Narxi: log hajmi ikki baravar (kuniga ~1 MB, diskda 93 GB bo'sh).

## 3. Portlar va kirish

GoAccess jonli rejimda ikkita port ishlatadi: HTML sahifa va uni yangilab
turadigan WebSocket. Ikkalasi ham **faqat `127.0.0.1`**.

| Port | Nima | Bog'lanish |
|---|---|---|
| 80, 443 | system nginx — **tegilmaydi** | `0.0.0.0` |
| 7890 | GoAccess WebSocket | `127.0.0.1` |
| 7891 | HTML hisobotni beradigan statik server | `127.0.0.1` |

Tashqaridan ikkalasi ham ko'rinmaydi (tekshirildi: `000`). Kirish faqat
SSH tunnel orqali — parol yo'q, brute-force qilinadigan yuza yo'q.

```bash
ssh goaccess
# brauzerda: http://localhost:7891
```

> 7890 ni ham uzatish **shart** — HTML ichida `ws://localhost:7890` manzili
> yozilgan. Faqat 7891 uzatilsa sahifa ochiladi-yu, raqamlar qotib qoladi.
> `~/.ssh/config` dagi `goaccess` hostida ikkalasi ham bor.

## 4. O'rnatilgan narsalar

| Fayl | Nima | Holat |
|---|---|---|
| `/etc/nginx/conf.d/goaccess-log.conf` | log formati + ikkinchi access_log | **yangi** |
| `/etc/goaccess/server.conf` | GoAccess sozlamasi | **yangi** |
| `/etc/systemd/system/goaccess.service` | jonli hisobot | **yangi** |
| `/etc/systemd/system/goaccess-web.service` | HTML server (127.0.0.1:7891) | **yangi** |
| `/etc/systemd/system/goaccess-restart.timer` | 00:05 da qayta ishga tushirish | **yangi** |
| `/var/log/nginx/all-access.log` | butun server trafigi | **yangi** |
| `/var/lib/goaccess/` | tarix bazasi | **yangi** |
| `/var/www/goaccess/index.html` | hisobot | **yangi** |
| `/etc/nginx/nginx.conf` | — | **o'zgarmagan** |
| `/etc/nginx/sites-available/bookshare` | — | **o'zgarmagan** |
| `/etc/nginx/sites-available/pochta` | — | **o'zgarmagan** |

Taymer nima uchun kerak: logrotate `/var/log/nginx/*.log` ni har kuni 00:00
da almashtiradi. GoAccess eski faylga yopishib qolmasligi uchun 00:05 da
qayta ishga tushiriladi. Tarix `--persist`/`--restore` tufayli yo'qolmaydi.

## 5. Ishlatish

```bash
ssh goaccess                  # tunnel ochiladi, oyna ochiq turgancha ishlaydi
ssh -f -N goaccess            # yoki fonda
```

Hisobotda **Virtual Hosts** paneli bor — u yerda `api99.e-tex.uz` (Pochta) va
`api.e-tex.uz` (BookShare) alohida ko'rinadi.

### Xizmat buyruqlari

```bash
ssh pochta 'systemctl status goaccess goaccess-web'
ssh pochta 'journalctl -u goaccess -n 50 --no-pager'
ssh pochta 'systemctl restart goaccess'

# Statistikani noldan boshlash:
ssh pochta 'systemctl stop goaccess; rm -rf /var/lib/goaccess/*; systemctl start goaccess'
```

## 6. To'liq orqaga qaytarish

Serverda ketma-ket:

```bash
systemctl disable --now goaccess goaccess-web goaccess-restart.timer
rm -f /etc/systemd/system/goaccess*.service /etc/systemd/system/goaccess*.timer
systemctl daemon-reload
rm -f /etc/nginx/conf.d/goaccess-log.conf
nginx -t && systemctl reload nginx
apt remove --purge -y goaccess
rm -rf /var/lib/goaccess /var/www/goaccess /etc/goaccess/server.conf
rm -f /var/log/nginx/all-access.log
```

Mavjud fayllar tahrirlanmagani uchun bu serverni boshlang'ich holatga
to'liq qaytaradi. Baseline zaxirasi: `/root/backup-before-npm/`.

---

# Bajarilish hisoboti — 2026-09-03

## Regressiya

| Nima | Baseline | Keyin |
|---|---|---|
| `api.e-tex.uz` (BookShare) | 404, nginx/1.24.0 | **404, nginx/1.24.0** |
| `api99.e-tex.uz/health` | 200 UP | **200 UP** |
| Netlify Mini App | 200 | **200** |
| 80 / 443 egasi | nginx | **nginx** |
| `nginx -t` | ok | **ok** |
| `sites-enabled` | bookshare, pochta | **bookshare, pochta** |
| `nginx.conf` | — | **bayt-bayt o'zgarmagan** |
| `sites-available/bookshare` | — | **bayt-bayt o'zgarmagan** |
| `sites-available/pochta` | — | **bayt-bayt o'zgarmagan** |
| `access.log` formati | combined | **combined** — o'zgarmagan |
| iptables qoidalari | 0 ta | **0 ta** |
| FORWARD siyosati | ACCEPT | **ACCEPT** |
| Docker | yo'q edi | **yo'q** |
| Bot webhook | ishlayapti | **ishlayapti**, navbat 0 |
| Tashqi 7890/7891 | — | **yopiq** (000) |

Yakuniy holat brauzer skrinshoti bilan tasdiqlandi: 16 so'rov, 16 yaroqli,
0 xato, javob vaqti ustunlari to'lgan, Virtual Hosts panelida ikkala domen
alohida ko'rinadi.

## To'rtta tuzoq (qayta uchramasin)

**1. systemd `%` belgisini o'zi talqin qiladi.** `ExecStart` ichiga
`--log-format=%h %^[%d:%t...` yozilsa, systemd `%h` ni foydalanuvchi uy
papkasiga aylantiradi va GoAccess "Format Errors" bilan yiqiladi. Format
**`/etc/goaccess/server.conf`** da turishi shart.

**2. `--persist` buzuq urinishlarni ham saqlaydi.** Formatni tuzatgandan
keyin ham eski xatolar hisobda qoldi (64 ta soxta "failed request").
Bazani tozalash kerak bo'ldi.

**3. `{0} @ {0/s}` — bu xato emas.** GoAccess parsing paytida shu
ko'rsatkichni chiqaradi va u yakuniy sonni ko'rsatmaydi. Haqiqiy tekshiruv:
`-o json` yoki `-o csv` bilan `valid_requests`. HTML'ni `grep` qilish
befoyda — jonli rejimda ma'lumot WebSocket orqali keladi, HTML'da faqat
panel tuzilmasi turadi.

**4. Server-darajadagi `access_log` http-darajadagini bekor qiladi.**
Boshida Pochta saytiga alohida log berilgandi; u holda o'sha sayt
http-darajadagi loglarga umuman yozmay qo'yadi. Butun server statistikasi
kerak bo'lgach o'sha qator olib tashlandi va faqat `conf.d` dagi
http-darajadagi log qoldirildi.

## Bilib qo'yish kerak

- **Vaqt zonasi:** hisobotdagi vaqtlar server zonasida (**CEST, +0200**),
  Toshkent emas. `--tz` qo'shilmadi: log formatida zona maydoni tashlab
  yuborilgan va noto'g'ri konversiya xavfi bor edi.
- **Tarix 2026-09-03 dan boshlanadi.** Undan oldingi loglarda domen maydoni
  yo'q — yangi formatga ko'chirib bo'lmaydi.
