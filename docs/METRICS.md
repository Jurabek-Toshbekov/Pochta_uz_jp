# METRICS.md — metrika ta'riflari

> Manba: `CLAUDE.md` §6.3. Har bir metrika bitta ta'rifga ega bo'lishi kerak —
> aks holda admin dashboard va hisobotlar bir-biriga qarama-qarshi raqam ko'rsatadi.
>
> Metrikalar SQL view sifatida yozilgan (`V5__admin_and_metrics.sql`) va
> dashboard **faqat** shu view'lardan o'qiydi. Bu fayl — o'sha view'larning shartnomasi.
> Butun loyihada analitika SQL'i ikki joyda: migratsiyadagi view'lar va
> `AdminAnalyticsService`. Controller'da SQL yo'q.

## Umumiy qoidalar

- Vaqt oynasi doim **UTC** kunlari bo'yicha (`date_trunc('day', occurred_at)`).
- Faqat `deleted_at IS NULL` bo'lgan yozuvlar hisobga olinadi (§1.1 — soft delete).
- Foydalanuvchi kesimida `user_id`, mehmon sessiyalari `session_id` bilan.
- Mediana — `percentile_cont(0.5)`, o'rtacha emas: bir dona anomaliya o'rtachani buzadi.

---

## 1. Voronka va UX

### Form funnel
**Ta'rif:** har bir qadamdan keyingi qadamga o'tgan sessiyalar ulushi.
**Manba:** `post_form_open` → `post_form_step_complete` (`step` bo'yicha) → `post_publish_success`.
**Nima uchun:** qaysi qadam odamni qochirayotganini faqat shu ko'rsatadi.

### Time-to-publish
**Ta'rif:** `post_publish_success` eventidagi `total_time_ms` xossasining medianasi.
Klient forma ochilishidan publishgacha o'tgan vaqtni o'zi o'lchab yuboradi — ikki
eventni `session_id` bo'yicha juftlashtirishdan ko'ra aniqroq, chunki foydalanuvchi
formani bir necha marta ochib yopishi mumkin.
**Nima uchun:** UX sifatining bitta raqamdagi o'lchovi. Maqsad: < 2 daqiqa (§8.2 "1 daqiqada").

### Abandon rate by step
**Ta'rif:** `post_form_abandon` soni / `post_form_step_view` soni — `step` kesimida.
**Nima uchun:** tuzatiladigan aniq joyni ko'rsatadi.

---

## 2. Bozor salomatligi

### Supply/Demand ratio
**Ta'rif:** `CARRY` e'lonlar soni / `SEND` e'lonlar soni — koridor × ISO hafta kesimida.
**Manba:** `posts` (`status='PUBLISHED'`).
**Nima uchun:** kimdan pul olish kerakligini shu aytadi. Nisbat < 1 bo'lsa kuryer
yetishmayapti (kuryerni jalb qilish kerak), > 1 bo'lsa yuk yetishmayapti.

### Fill rate
**Ta'rif:** kamida bitta `contact_reveal` olgan e'lonlar ulushi / barcha publish bo'lgan e'lonlar.
**Manba:** `contact_reveals` × `posts`.
**Nima uchun:** mahsulot ishlayaptimi yoki yo'qmi — asosiy savol.

### Match latency
**Ta'rif:** `posts.published_at` va o'sha e'lonning birinchi `contact_reveals.created_at`
orasidagi vaqt medianasi.
**Nima uchun:** tezlik = qiymat. Sekinlashsa — taklif kamaygan.

### Price index
**Ta'rif:** kg narxining medianasi — yo'nalish (`origin_airport` → `dest_airport`) ×
oy × **valyuta** kesimida. `price_unit='PER_KG'` bo'lsa narx to'g'ridan-to'g'ri,
`TOTAL` bo'lsa `price_amount / weight_kg`. `NEGOTIABLE` indeksga kirmaydi — raqami yo'q.
**Diqqat:** valyutalar bir-biriga keltirilmaydi, chunki tarixiy kurs jadvali hali yo'q.
Shuning uchun har bir valyuta alohida qator. Kurs jadvali qo'shilganda bu view'ga
yangi ustun qo'shiladi (mavjudi o'zgartirilmaydi).
**Nima uchun:** sotiladigan mahsulot. Kanalga oylik post ham bo'ladi.
**Shart:** narx birligi majburiy bo'lgani uchun mumkin (§6.4, 8-band).

---

## 3. Qoplanmagan talab

### Zero-result rate
**Ta'rif:** `search_queries` dagi `result_count = 0` bo'lgan qidiruvlar ulushi.
**Nima uchun:** odamlar izlayapti, lekin topmayapti — bu o'sish yo'nalishi.

### Top zero-result routes
**Ta'rif:** `result_count = 0` bo'lgan qidiruvlardagi `(origin_airport, dest_airport)`
juftliklari, soni bo'yicha tartiblangan top-50.
**Nima uchun:** qayerga o'sish kerakligini aniq aytadi. Admin'ning eng qimmatli sahifasi (§11.2).

### Search → click conversion
**Ta'rif:** `search_result_click` bo'lgan qidiruvlar / `search_performed`.
**Nima uchun:** natijalar mos kelyaptimi yoki shovqinmi.

---

## 4. Foydalanuvchi salomatligi

### DAU / WAU / MAU
**Ta'rif:** oynada kamida bitta event yozgan noyob `user_id` soni (1 / 7 / 30 kun).

### D1 / D7 / D30 retention
**Ta'rif:** `users.first_seen_at` bo'yicha kogorta; N-kunda kamida bitta event
yozgan foydalanuvchilar ulushi.
**Nima uchun:** mahsulot yopishqoqmi yoki bir martalik ishmi.

### Repeat poster rate
**Ta'rif:** 30 kun ichida ≥2 e'lon bergan foydalanuvchilar ulushi.
**Nima uchun:** takror e'lon beruvchilar — to'lovchi segment (VIP, 6-bosqich).

### Seasonality
**Ta'rif:** e'lon soni — hafta kuni va oy kesimida.
**Nima uchun:** ta'til, Navro'z, yangi o'quv yili — kampaniyani rejalash uchun.

---

## 5. Xabarnoma va ishonch

### Notification CTR
**Ta'rif:** `notification_opened` / `notification_sent`.
**Nima uchun:** past CTR = spam. 5% dan tushsa xabarnoma mantiqini qayta ko'rish kerak.

### Notification conversion
**Ta'rif:** `notification_converted` / `notification_sent`.

### Report rate
**Ta'rif:** `reports` soni / publish bo'lgan e'lonlar soni.
**Nima uchun:** xavfsizlik salomatligi. O'sib ketsa moderatsiya majburiy qilinadi.

### Deal confirmation rate
**Ta'rif:** `posts.deal_confirmed_at IS NOT NULL` bo'lgan e'lonlar / publish bo'lganlar.
**Manba:** publish + 3 kundan keyingi "Topildimi?" so'rovi (§6.4, 1-band).
**Nima uchun:** GMV va haqiqiy fill rate'ni bilishning yagona yo'li.
**Fill rate'dan farqi:** fill rate kontakt ochilganini sanaydi (niyat), bu esa
natijani. Ikkisi orasidagi farq muzokara bosqichidagi uzilishni ko'rsatadi.

### Ishonch balli (trust_score)
**Ta'rif:** `10 (bazaviy) + baho×10 (maks 50) + yakunlangan bitim×5 (maks 20)
+ tasdiqlanish (PHONE 10 / DOCUMENT 20) − asosli shikoyat×10 (maks 40)`, 0..100.
**Manba:** `TrustScoreService` — sharh, bitim va shikoyat hal qilinganda qayta hisoblanadi.
**Nima uchun faqat ASOSLI shikoyat:** ochiq shikoyat hali tekshirilmagan; u bilan
ball tushirish raqobatchiga qurol berish bo'lardi.

---

---

## 6. Metrika → view → endpoint

Har bir metrikaning aniq manzili. View'lar parametr qabul qilmaydi: sana oralig'i
chaqiruvchi tomonda `WHERE` bilan qo'yiladi, shuning uchun bitta view ham
dashboard'ga, ham kunlik job'ga xizmat qiladi.

| Metrika | View | Admin endpoint |
|---|---|---|
| E'lon oqimi | `v_metrics_post_daily` | `GET /api/admin/analytics/posts-daily` |
| Form funnel | `v_metrics_funnel_daily` | `GET /api/admin/analytics/funnel` |
| Abandon by step | `v_metrics_abandon_daily` | `GET /api/admin/analytics/abandon` |
| Price index | `v_metrics_price_index` | `GET /api/admin/analytics/price-index` |
| Supply/Demand | `v_metrics_supply_demand` | `GET /api/admin/analytics/supply-demand` |
| Fill rate | `v_metrics_fill_rate` | `GET /api/admin/overview` |
| Match latency | `v_metrics_match_latency` | `GET /api/admin/analytics/match-latency` |
| Zero-result routes | `v_metrics_zero_result_routes` | `GET /api/admin/search-insights/zero-results` |
| Zero-result rate | `v_metrics_search_daily` | `GET /api/admin/search-insights/daily` |
| DAU | `v_metrics_active_users` | `GET /api/admin/overview` |
| D1/D7/D30 retention | `v_metrics_cohort_retention` | `GET /api/admin/analytics/cohorts` |
| Time-to-publish | `v_metrics_time_to_publish` | `GET /api/admin/overview` |
| Repeat poster rate | `v_metrics_repeat_posters` | — (kunlik agregatda) |
| Seasonality | `v_metrics_seasonality` | `GET /api/admin/analytics/seasonality` |
| Notification CTR | `v_metrics_notifications` | `GET /api/admin/notifications/stats` |
| Report rate | `v_metrics_report_rate` | — (kunlik agregatda) |
| Deal confirmation rate | `v_metrics_deal_confirmation` | `GET /api/admin/analytics/deal-confirmation` |
| Yopish sabablari | `v_metrics_close_reasons` | `GET /api/admin/analytics/close-reasons` |
| Reytting taqsimoti | `v_metrics_reviews` | `GET /api/admin/analytics/reviews` |

### Kunlik agregatlar

`daily_metrics` jadvali — kalit-qiymat shakli: `(metric_date, metric_key, dimension)`.
Har kecha **03:15 UTC** da `DailyMetricsJob` o'tgan kunni hisoblab yozadi.
Job idempotent (`ON CONFLICT DO UPDATE`), shuning uchun orqaga qarab to'ldirish ham
shu metod bilan qilinadi.

Hozir yoziladigan kalitlar: `posts_created`, `posts_published`,
`posts_created_by_type`, `posts_created_by_direction`, `dau`, `searches`,
`searches_zero_result`, `funnel_users` (dimension = qadam kaliti),
`time_to_publish_median_seconds`.

**Nima uchun kerak:** raw event'lar 24 oydan keyin tozalanishi mumkin (§6.2),
bu jadval esa abadiy qoladi.

---

## Amalga oshirilgan holati

| Metrika guruhi | Holat |
|---|---|
| Xom ma'lumot (events, search_queries, contact_reveals) | ✅ `V1__init.sql` |
| Voronka view'lari | ✅ `V5__admin_and_metrics.sql` |
| Price index / supply-demand view'lari | ✅ `V5__admin_and_metrics.sql` |
| `daily_metrics` + scheduled job | ✅ `DailyMetricsJob` |
| Admin dashboard | ✅ `admin/` (React + Recharts) |
| Deal confirmation rate | ✅ `DealService` + `PostFollowUpJob` (publish + 3 kun) |
| Notification CTR (real ma'lumot) | ✅ `NotificationService` (digest + `startapp=nt_` atributsiyasi) |
| Ishonch balli (`trust_score`) | ✅ `TrustScoreService` |
| Valyutalar bo'yicha yagona narx indeksi | ⏳ kurs jadvali kerak |
