-- =====================================================================
-- V2__seed_reference.sql — reference ma'lumot (CLAUDE.md §5.2)
--
-- Aeroportlar IATA kodi bilan, koordinatalar bilan (kelajakda masofa
-- hisoblash va xaritada ko'rsatish uchun). Erkin matn ishlatilmaydi —
-- e'lon aeroport kodiga bog'lanadi (§5.1).
-- =====================================================================

-- ============ KORIDOR ============
INSERT INTO corridors (code, origin_country, dest_country, title_uz, is_active) VALUES
    ('JP_UZ', 'JP', 'UZ', 'Yaponiya ↔ O''zbekiston', TRUE);

-- ============ AEROPORTLAR ============
-- Yaponiya
INSERT INTO airports (code, country_code, city_uz, city_ru, city_en, name_en, latitude, longitude, is_popular, sort_order) VALUES
    ('NRT', 'JP', 'Tokio',    'Токио',    'Tokyo',    'Narita International Airport',                35.764700, 140.386400, TRUE,  10),
    ('HND', 'JP', 'Tokio',    'Токио',    'Tokyo',    'Tokyo Haneda International Airport',          35.549400, 139.779800, TRUE,  20),
    ('KIX', 'JP', 'Osaka',    'Осака',    'Osaka',    'Kansai International Airport',                34.434700, 135.244000, TRUE,  30),
    ('NGO', 'JP', 'Nagoya',   'Нагоя',    'Nagoya',   'Chubu Centrair International Airport',        34.858400, 136.805400, FALSE, 40),
    ('FUK', 'JP', 'Fukuoka',  'Фукуока',  'Fukuoka',  'Fukuoka Airport',                             33.585900, 130.450700, FALSE, 50),
    ('CTS', 'JP', 'Sapporo',  'Саппоро',  'Sapporo',  'New Chitose Airport',                         42.775200, 141.692300, FALSE, 60);

-- O'zbekiston
INSERT INTO airports (code, country_code, city_uz, city_ru, city_en, name_en, latitude, longitude, is_popular, sort_order) VALUES
    ('TAS', 'UZ', 'Toshkent',  'Ташкент',   'Tashkent',  'Tashkent International Airport',           41.257900,  69.281200, TRUE,  10),
    ('SKD', 'UZ', 'Samarqand', 'Самарканд', 'Samarkand', 'Samarkand International Airport',          39.700500,  66.983800, TRUE,  20),
    ('BHK', 'UZ', 'Buxoro',    'Бухара',    'Bukhara',   'Bukhara International Airport',            39.775000,  64.483300, FALSE, 30),
    ('UGC', 'UZ', 'Urganch',   'Ургенч',    'Urgench',   'Urgench International Airport',            41.584300,  60.641700, FALSE, 40),
    ('NMA', 'UZ', 'Namangan',  'Наманган',  'Namangan',  'Namangan International Airport',           40.984700,  71.556700, FALSE, 50),
    ('FEG', 'UZ', 'Farg''ona', 'Фергана',   'Fergana',   'Fergana International Airport',            40.358800,  71.745000, FALSE, 60);

-- ============ YUK KATEGORIYALARI ============
-- risk_level = HIGH bo'lganda Mini App publish'dan oldin warning_uz ni
-- alohida ekranda ko'rsatadi (§7.3).
INSERT INTO cargo_categories (code, title_uz, title_ru, emoji, risk_level, warning_uz, sort_order) VALUES
    ('DOCUMENTS',   'Hujjatlar',          'Документы',       '📄', 'LOW',    NULL, 10),
    ('CLOTHES',     'Kiyim-kechak',       'Одежда',          '👕', 'LOW',    NULL, 20),
    ('ELECTRONICS', 'Elektronika',        'Электроника',     '📱', 'MEDIUM',
        'Litiy batareyali qurilmalar aviatashuvda cheklangan. Powerbank faqat qo''l yukida tashiladi.', 30),
    ('SUPPLEMENTS', 'Sport ozuqasi',      'Спортпит',        '💪', 'HIGH',
        'Sport ozuqasi va vitaminlar miqdoriga chegara bor. Tarkibini tekshiring — ba''zi komponentlar Yaponiyada taqiqlangan.', 40),
    ('FOOD',        'Oziq-ovqat',         'Продукты',        '🍱', 'HIGH',
        'Yaponiyaga go''sht va sut mahsulotlarini olib kirish taqiqlangan. Chegarada olib qo''yiladi va jarima solinadi.', 50),
    ('MEDICINE',    'Dori-darmon',        'Лекарства',       '💊', 'HIGH',
        'Retseptli dorilar va tarkibida psevdoefedrin bo''lgan preparatlar taqiqlangan. Ba''zi dorilar uchun Yakkan Shoumei ruxsatnomasi kerak.', 60),
    ('OTHER',       'Boshqa',             'Другое',          '📦', 'MEDIUM',
        'Yuk tarkibini yuk egasi oldida ochib tekshiring — yopiq qutini qabul qilmang.', 70);
