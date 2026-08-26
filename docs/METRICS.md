# METRICS.md — metrika ta'riflari

> Manba: `CLAUDE.md` §6.3. Har bir metrika bitta ta'rifga ega bo'lishi kerak —
> aks holda admin dashboard va hisobotlar bir-biriga qarama-qarshi raqam ko'rsatadi.
>
> Metrikalar 4-bosqichda SQL view sifatida yoziladi (`V*__metrics_views.sql`) va
> dashboard **faqat** shu view'lardan o'qiydi. Bu fayl — o'sha view'larning shartnomasi.

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
**Ta'rif:** `post_form_open` va `post_publish_success` orasidagi vaqt medianasi (bitta `session_id` ichida).
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
**Ta'rif:** ¥/kg medianasi — yo'nalish (`origin_airport` → `dest_airport`) × oy kesimida.
Faqat `price_unit='PER_KG'` va `price_currency='JPY'` bo'lganlar; boshqa valyutalar
o'sha oyning o'rtacha kursi bilan keltiriladi (kurs jadvali 4-bosqichda).
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
**Ta'rif:** `deal_confirmed` soni / publish bo'lgan e'lonlar soni.
**Manba:** publish + 3 kundan keyingi "Topildimi?" so'rovi (§6.4, 1-band).
**Nima uchun:** GMV va haqiqiy fill rate'ni bilishning yagona yo'li.

---

## Amalga oshirilgan holati

| Metrika guruhi | Holat |
|---|---|
| Xom ma'lumot (events, search_queries, contact_reveals) | ✅ `V1__init.sql` |
| Voronka view'lari | ⏳ 4-bosqich |
| Price index / supply-demand view'lari | ⏳ 4-bosqich |
| `daily_metrics` materialized view + scheduled job | ⏳ 4-bosqich |
| Admin dashboard | ⏳ 4-bosqich |
