# EVENTS.md — event taksonomiyasi

> Manba: `CLAUDE.md` §6.1. Bu fayl — amalda yozilayotgan event'larning yagona ro'yxati.
> Yangi event qo'shilganda: `EventName.java` + shu fayl + (klient yuborsa) `CLIENT_ALLOWED`.

## Umumiy struktura

Har bir event `events` jadvaliga yoziladi (append-only, §1.1):

| Ustun | Ta'rif |
|---|---|
| `event_name` | quyidagi ro'yxatdagi nom, maks 64 belgi |
| `user_id` | `users.id` — **faqat tekshirilgan `initData`dan** (§7.1) |
| `session_id` | Mini App ochilganda generatsiya qilinadi, `sessionStorage`da turadi |
| `post_id` | tegishli e'lon (bo'lsa) |
| `source` | `MINIAPP` \| `BOT` \| `CHANNEL` \| `ADMIN` \| `SYSTEM` |
| `platform` | `ios` \| `android` \| `tdesktop` \| `web` |
| `properties` | JSONB, maks 32 kalit, matn qiymati maks 512 belgi |
| `occurred_at` | klient vaqti (kelmasa server vaqti), UTC |

**PII taqiqi (§1.7).** `EventLogger` quyidagi kalitlarni saqlashdan oldin tashlab yuboradi:
`phone`, `phone_number`, `telefon`, `contact`, `contact_phone`, `first_name`, `last_name`,
`full_name`, `ism`, `email`, `init_data`, `token`.

## Klient qanday yuboradi

`POST /api/miniapp/events` — 10 event yoki 5 soniyada bir marta batch:

```json
{ "events": [
  { "name": "app_open", "sessionId": "…", "platform": "ios",
    "properties": { "start_param": "ch_…", "is_first_open": true } }
] }
```

- Javob: `202 Accepted` (yozish `@Async`, so'rovni to'xtatmaydi).
- Bir so'rovda maksimal **100** event, aks holda `400`.
- Ro'yxatda yo'q nom **butun batch'ni yiqitmaydi** — faqat o'sha event tashlab yuboriladi.
- Chegara: 60 so'rov/daqiqa (§7.2), oshsa `429` + `rate_limit_hit`.

## Klientdan qabul qilinadigan event'lar

`EventName.CLIENT_ALLOWED` — faqat shular. Qolganlarini server o'zi yozadi, chunki
ularga ishonib bo'lmaydi (masalan `post_publish_success`ni klient yozsa, konversiya
metrikasi soxtalashadi).

## 1. Sessiya va kirish

| Event | Manba | Properties |
|---|---|---|
| `app_open` | MINIAPP | `start_param`, `platform`, `tg_version`, `color_scheme`, `is_first_open` |
| `app_close` | MINIAPP | `duration_ms`, `screens_visited` |
| `bot_command` | BOT | `command` |
| `deep_link_open` | MINIAPP | `start_param`, `resolved_post_id` |
| `language_changed` | MINIAPP | `from`, `to` |

## 2. E'lon berish voronkasi (eng muhim)

| Event | Manba | Properties |
|---|---|---|
| `post_form_open` | MINIAPP | `entry_point` (`bot_button` \| `search_empty` \| `my_posts`) |
| `post_form_step_view` | MINIAPP | `step`, `step_index` |
| `post_form_step_complete` | MINIAPP | `step`, `time_on_step_ms`, `edit_count` |
| `post_form_step_back` | MINIAPP | `step`, `from_step` |
| `post_form_field_error` | MINIAPP | `field`, `error_code` |
| `post_form_abandon` | MINIAPP | `last_step`, `time_total_ms`, `filled_fields[]` |
| `post_draft_saved` | MINIAPP | `step`, `completeness_pct` |
| `post_preview_view` | MINIAPP | `post_id` |
| `safety_checklist_view` | MINIAPP | — |
| `safety_checklist_accept` | MINIAPP | `time_on_screen_ms` |
| `post_submit` | **SERVER** | `post_id`, `post_type`, `direction`, `route`, `categories[]`, `price`, `currency`, `days_until_departure` |
| `post_publish_success` | **SERVER** | `post_id`, `channel_message_id`, `total_time_ms` |
| `post_publish_fail` | **SERVER** | `post_id`, `error_code` |
| `post_edit` | **SERVER** | `post_id`, `changed_fields[]` |
| `post_close` | **SERVER** | `post_id`, `reason`, `hours_since_publish` |

## 3. Qidiruv (ikkinchi eng muhim)

| Event | Manba | Properties |
|---|---|---|
| `search_open` | MINIAPP | `entry_point` |
| `search_performed` | **SERVER** | `filters{}`, `result_count`, `latency_ms` |
| `search_zero_result` | **SERVER** | `filters{}` — qoplanmagan talab, oltin ma'lumot |
| `search_filter_change` | MINIAPP | `filter`, `value` |
| `search_result_click` | MINIAPP | `post_id`, `position`, `result_count` |
| `search_saved` | **SERVER** | `filters{}` → obunaga aylanadi |

Qidiruvning o'zi qo'shimcha ravishda `search_queries` jadvaliga ham yoziladi (§10.2) —
event JSONB'siga emas, tipli ustunlarga, chunki narx indeksi va talab/taklif
hisoblari shu jadvaldan o'qiladi.

## 4. Qiymat momenti

| Event | Manba | Properties |
|---|---|---|
| `post_view` | MINIAPP | `post_id`, `source` |
| `post_detail_view` | MINIAPP | `post_id`, `time_on_screen_ms` |
| `contact_reveal` | **SERVER** | `post_id`, `owner_id`, `channel` — bitim boshlangan payt |
| `contact_click` | MINIAPP | `post_id`, `method` (`telegram` \| `phone`) |
| `post_share` | MINIAPP | `post_id`, `target` |
| `deal_confirmed` | **SERVER** | `post_id`, `counterpart_id` |
| `review_left` | **SERVER** | `post_id`, `rating` |

## 5. Xabarnoma

| Event | Manba | Properties |
|---|---|---|
| `notification_sent` | SYSTEM | `subscription_id`, `post_id` |
| `notification_opened` | MINIAPP | `notification_id`, `post_id` |
| `notification_converted` | **SERVER** | `notification_id`, `post_id` — ochib, kontakt ochgan |

## 6. Xavfsizlik

| Event | Manba | Properties |
|---|---|---|
| `report_submitted` | **SERVER** | `post_id`, `reason` |
| `post_rejected` | ADMIN | `post_id`, `reason` |
| `user_blocked` | ADMIN | `target_user_id`, `reason` |
| `rate_limit_hit` | **SERVER** | `endpoint`, `limit`, `window_seconds` |

## Retention (§6.2)

- Xom event'lar — **24 oy**.
- Kunlik agregatlar (`daily_metrics`) — **abadiy**.
- Event'lar hech qachon `UPDATE` yoki `DELETE` qilinmaydi (§1.1).

## Amalga oshirilgan holati

| Qism | Holat |
|---|---|
| `events` jadvali + indekslar | ✅ `V1__init.sql` |
| `EventLogger` (async, PII filtri) | ✅ `analytics/EventLogger.java` |
| `POST /api/miniapp/events` | ✅ `api/miniapp/EventController.java` |
| `rate_limit_hit` | ✅ `service/RateLimitService.java` |
| Voronka event'lari (klient) | ✅ `miniapp/src/analytics/` — batch 10/5s, PII manbada ham yuborilmaydi |
| `post_submit` / `post_publish_*` (server) | ✅ `PostService`, `PublishService` |
| `bot_command`, `deep_link_open`, `language_changed` (bot) | ✅ `bot/BotUpdateHandler.java` |
| Qidiruv event'lari | ⏳ 3-bosqich |
| `daily_metrics` agregati | ⏳ 4-bosqich |
