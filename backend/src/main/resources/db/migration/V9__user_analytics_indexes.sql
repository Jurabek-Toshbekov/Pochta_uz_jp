-- Admin -> foydalanuvchi profili: "kim kimning e'lonini ko'rdi".
--
-- Bu so'rovlar events ni post_id bo'yicha, contact_reveals ni esa ko'ruvchi va
-- egasi bo'yicha qidiradi. V1 dagi indekslar (event_name+vaqt, user_id+vaqt,
-- reveal post_id) bularni qoplamaydi va events o'sgan sari to'liq skanerlash
-- sekinlashadi. Faqat indeks qo'shiladi — ma'lumot va sxema o'zgarmaydi.

CREATE INDEX idx_events_post
    ON events (post_id, event_name)
    WHERE post_id IS NOT NULL;

CREATE INDEX idx_reveal_viewer ON contact_reveals (viewer_id, created_at DESC);
CREATE INDEX idx_reveal_owner  ON contact_reveals (owner_id, created_at DESC);
