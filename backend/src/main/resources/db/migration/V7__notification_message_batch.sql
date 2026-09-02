-- =====================================================================
-- V7 — xabarnoma kunlik chegarasini XABAR bo'yicha sanash (§10.3)
--
-- Muammo: `notifications_sent` da bitta qator = bitta MOS E'LON, lekin
-- digest ularni BITTA xabarga birlashtirib yuboradi. Kunlik chegara
-- ("kuniga maksimal 5 ta xabarnoma") qatorlarni sanaydi, shuning uchun
-- 6 ta mos e'lonli bitta digest chegarani darhol to'ldirib qo'yadi va
-- foydalanuvchi bitta xabar olgan bo'lsa ham kun oxirigacha bloklanadi.
--
-- Yechim: har bir HAQIQATDA YUBORILGAN XABAR uchun bitta `batch_id`.
-- Digest — bitta batch (nechta e'lon bo'lsa ham), `sendOnce` bilan
-- ketadigan har bir xabar — o'z batch'i. Chegara `count(distinct batch_id)`.
--
-- Ustun nullable: eski qatorlarda ham qiymat bo'lishi uchun quyida
-- to'ldiriladi, lekin NOT NULL qilinmaydi — navbatdagi (PENDING) qator
-- hali yuborilmagan, uning xabari ham yo'q.
-- =====================================================================

ALTER TABLE notifications_sent ADD COLUMN batch_id UUID;

COMMENT ON COLUMN notifications_sent.batch_id IS
    'Bitta yuborilgan xabar identifikatori. Digest ichidagi barcha qatorlarda bir xil.';

-- Eski qatorlarni to'ldirish: bir foydalanuvchiga bir xil turda, bir
-- soniya ichida yozilganlar aynan bitta digest bo'lgan.
UPDATE notifications_sent n
SET batch_id = g.batch_id
FROM (
    SELECT user_id,
           kind,
           date_trunc('second', created_at) AS sec,
           gen_random_uuid()                AS batch_id
    FROM notifications_sent
    WHERE status <> 'PENDING'
    GROUP BY user_id, kind, date_trunc('second', created_at)
) g
WHERE n.status <> 'PENDING'
  AND n.user_id = g.user_id
  AND n.kind = g.kind
  AND date_trunc('second', n.created_at) = g.sec;

-- Kunlik chegara so'rovi: user + status + vaqt oynasi -> distinct batch.
CREATE INDEX idx_notifications_user_batch
    ON notifications_sent(user_id, status, created_at DESC);
