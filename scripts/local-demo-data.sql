-- =====================================================================
-- local-demo-data.sql — FAQAT LOKAL SINOV UCHUN
--
-- Bu migratsiya EMAS. Flyway uni ko'rmaydi va prod'da hech qachon
-- ishlatilmaydi. Vazifasi: qidiruv ekranini bo'sh emas holatda ko'rish.
--
-- Nima uchun kerak: e'lon `PUBLISHED` bo'lishi uchun kanalga chiqishi kerak,
-- kanalga chiqish uchun haqiqiy bot tokeni kerak. Lokal sinovda token yo'q,
-- shuning uchun demo e'lonlar to'g'ridan-to'g'ri qo'yiladi.
--
-- Ishlatish:
--   psql -U postgres -d pochta -f scripts/local-demo-data.sql
--
-- Qayta ishga tushirsa — avvalgi demo ma'lumot o'chiriladi va yangisi
-- qo'yiladi (faqat demo foydalanuvchilar, boshqasiga tegmaydi).
-- =====================================================================

BEGIN;

-- Demo foydalanuvchilar: telegram_id 9_000_00x oralig'ida.
DELETE FROM post_categories WHERE post_id IN (
    SELECT p.id FROM posts p JOIN users u ON u.id = p.user_id
    WHERE u.telegram_id BETWEEN 900000001 AND 900000099);
DELETE FROM contact_reveals WHERE post_id IN (
    SELECT p.id FROM posts p JOIN users u ON u.id = p.user_id
    WHERE u.telegram_id BETWEEN 900000001 AND 900000099);
DELETE FROM posts WHERE user_id IN (
    SELECT id FROM users WHERE telegram_id BETWEEN 900000001 AND 900000099);
DELETE FROM users WHERE telegram_id BETWEEN 900000001 AND 900000099;

INSERT INTO users (id, telegram_id, username, first_name, ui_language,
                   verification_level, trust_score, consent_tos_at, consent_privacy_at)
VALUES
    ('11111111-1111-4111-8111-111111111111', 900000001, 'aziz_courier', 'Aziz',  'uz', 'PHONE',    42, now(), now()),
    ('22222222-2222-4222-8222-222222222222', 900000002, 'dilnoza_send', 'Dilnoza','uz', 'NONE',      5, now(), now()),
    ('33333333-3333-4333-8333-333333333333', 900000003, 'sardor_jp',    'Sardor', 'ru', 'DOCUMENT', 88, now(), now());

-- ---------------------------------------------------------------------
-- E'lonlar. Turli yo'nalish, sana, narx va kategoriya — filtrlarni
-- sinash uchun ataylab xilma-xil.
-- ---------------------------------------------------------------------
INSERT INTO posts (
    id, user_id, corridor_id, post_type, direction,
    origin_airport, dest_airport, final_destination,
    depart_date, deadline_date, date_flexible_days,
    weight_kg, weight_kg_max, price_amount, price_currency, price_unit,
    comment, contact_telegram, contact_phone, safety_checklist_ok, safety_checked_at,
    status, published_at, expires_at, view_count, source)
SELECT
    v.id, v.user_id, (SELECT id FROM corridors WHERE code = 'JP_UZ'),
    v.post_type, v.direction, v.origin, v.dest, v.final_dest,
    v.depart_date, v.deadline_date, v.flex,
    v.weight_min, v.weight_max, v.price, v.currency, v.unit,
    v.comment, v.tg, v.phone, TRUE, now(),
    'PUBLISHED', v.published_at, v.expires_at, v.views, 'MINIAPP'
FROM (VALUES
    ('aaaa1111-0000-4000-8000-000000000001'::uuid, '11111111-1111-4111-8111-111111111111'::uuid,
     'CARRY', 'JP_UZ', 'NRT', 'TAS', 'Toshkent',
     CURRENT_DATE + 5, NULL::date, 2::smallint,
     5.00, 20.00, 2000.00, 'JPY', 'PER_KG',
     'Hujjat, kiyim va noutbuk olib ketaman. Qo''l yukida joy bor.',
     'aziz_courier', '+998901112233', now() - interval '2 hours',
     (CURRENT_DATE + 8)::timestamptz, 34),

    ('aaaa1111-0000-4000-8000-000000000002'::uuid, '33333333-3333-4333-8333-333333333333'::uuid,
     'CARRY', 'JP_UZ', 'KIX', 'SKD', 'Samarqand',
     CURRENT_DATE + 12, NULL::date, 0::smallint,
     10.00, 23.00, 1500.00, 'JPY', 'PER_KG',
     'Osaka - Samarqand. Faqat quruq mahsulot, dori olmayman.',
     'sardor_jp', NULL, now() - interval '1 day', (CURRENT_DATE + 14)::timestamptz, 112),

    ('aaaa1111-0000-4000-8000-000000000003'::uuid, '22222222-2222-4222-8222-222222222222'::uuid,
     'SEND', 'JP_UZ', 'HND', 'TAS', NULL,
     NULL::date, CURRENT_DATE + 9, 3::smallint,
     3.00, NULL::numeric, NULL::numeric, NULL, 'NEGOTIABLE',
     'Onamga vitamin va kichik sovg''a yuborishim kerak. Narxni kelishamiz.',
     'dilnoza_send', '+998935556677', now() - interval '5 hours',
     (CURRENT_DATE + 12)::timestamptz, 7),

    ('aaaa1111-0000-4000-8000-000000000004'::uuid, '11111111-1111-4111-8111-111111111111'::uuid,
     'CARRY', 'UZ_JP', 'TAS', 'NRT', 'Yokohama',
     CURRENT_DATE + 20, NULL::date, 5::smallint,
     8.00, 15.00, 12.00, 'USD', 'PER_KG',
     'Toshkentdan Tokioga. Quruq meva va shirinlik olib ketaman.',
     'aziz_courier', '+998901112233', now() - interval '3 days',
     (CURRENT_DATE + 22)::timestamptz, 51),

    ('aaaa1111-0000-4000-8000-000000000005'::uuid, '33333333-3333-4333-8333-333333333333'::uuid,
     'CARRY', 'JP_UZ', 'FUK', 'BHK', 'Buxoro',
     CURRENT_DATE + 2, NULL::date, 0::smallint,
     2.00, 6.00, 350000.00, 'UZS', 'TOTAL',
     'Fukuoka - Buxoro. Kichik paket, tez yetkazaman.',
     'sardor_jp', NULL, now() - interval '30 minutes',
     (CURRENT_DATE + 4)::timestamptz, 3)
) AS v(id, user_id, post_type, direction, origin, dest, final_dest,
       depart_date, deadline_date, flex,
       weight_min, weight_max, price, currency, unit,
       comment, tg, phone, published_at, expires_at, views);

-- Kategoriyalar: 1=DOCUMENTS 2=CLOTHES 3=ELECTRONICS 4=SUPPLEMENTS 5=FOOD 6=MEDICINE 7=OTHER
INSERT INTO post_categories (post_id, category_id) VALUES
    ('aaaa1111-0000-4000-8000-000000000001', 1),
    ('aaaa1111-0000-4000-8000-000000000001', 2),
    ('aaaa1111-0000-4000-8000-000000000001', 3),
    ('aaaa1111-0000-4000-8000-000000000002', 2),
    ('aaaa1111-0000-4000-8000-000000000002', 7),
    ('aaaa1111-0000-4000-8000-000000000003', 4),
    ('aaaa1111-0000-4000-8000-000000000003', 6),
    ('aaaa1111-0000-4000-8000-000000000004', 5),
    ('aaaa1111-0000-4000-8000-000000000005', 1);

COMMIT;

-- Nazorat
SELECT p.status, p.direction, p.origin_airport || ' -> ' || p.dest_airport AS route,
       p.price_amount, p.price_currency, p.price_unit, u.username
FROM posts p JOIN users u ON u.id = p.user_id
WHERE u.telegram_id BETWEEN 900000001 AND 900000099
ORDER BY p.published_at DESC;
