-- Admin <-> foydalanuvchi yozishmasi (bot orqali).
--
-- Admin panelda foydalanuvchiga xabar yoziladi, bot uni yetkazadi. Foydalanuvchi
-- botga javob yozsa, u ham shu yerga tushadi va panelda ko'rinadi.
--
-- OUTBOUND — admin yozgan (admin_id majburiy), delivery_status: SENT | FAILED
--            (FAILED — odatda foydalanuvchi botni bloklagan).
-- INBOUND  — foydalanuvchi javobi (admin_id yo'q), delivery_status: RECEIVED;
--            read_at — admin panelda ochilgan payt ("o'qilmagan" hisoblagichi uchun).
--
-- Xabarlar o'chirilmaydi (§1.1): faqat deleted_at.

CREATE TABLE admin_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    direction       VARCHAR(8) NOT NULL
        CHECK (direction IN ('OUTBOUND','INBOUND')),
    admin_id        UUID REFERENCES users(id),
    body            TEXT NOT NULL
        CHECK (char_length(body) BETWEEN 1 AND 4000),
    delivery_status VARCHAR(16) NOT NULL
        CHECK (delivery_status IN ('SENT','FAILED','RECEIVED')),
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_admin_messages_author
        CHECK ((direction = 'OUTBOUND') = (admin_id IS NOT NULL))
);

CREATE INDEX idx_admin_messages_user
    ON admin_messages (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_admin_messages_unread
    ON admin_messages (created_at DESC)
    WHERE direction = 'INBOUND' AND read_at IS NULL AND deleted_at IS NULL;
