#!/usr/bin/env bash
# =====================================================================
# Bot tokenini .env fayliga yozadi — FAQAT LOKAL SINOV UCHUN.
#
# Nima uchun skript: tokenni matn tahrirlagichda qo'yishda ikki xato
# tez-tez uchraydi —
#   1) Notepad faylni `.env.txt` qilib saqlab qo'yadi
#   2) qator oxirida ko'rinmas CR qoladi va Telegram 401 qaytaradi
# Skript ikkalasini ham oldini oladi.
#
# Token EKRANDA KO'RINMAYDI va terminal tarixiga tushmaydi (argument
# sifatida emas, so'rov bilan kiritiladi).
#
# Ishlatish (loyiha ildizidan, O'Z terminalingizda):
#   bash scripts/set-bot-token.sh
# =====================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "XATO: $ENV_FILE topilmadi." >&2
    exit 1
fi

printf 'Bot tokenini kiriting (ko%s rinmaydi), keyin Enter: ' "'"
read -rs TOKEN
echo ""

TOKEN="${TOKEN//$'\r'/}"
TOKEN="${TOKEN//$'\n'/}"
TOKEN="$(printf '%s' "$TOKEN" | tr -d '[:space:]')"

if [ -z "$TOKEN" ]; then
    echo "XATO: bo'sh qiymat. Hech narsa o'zgartirilmadi." >&2
    exit 1
fi
if ! printf '%s' "$TOKEN" | grep -qE '^[0-9]{5,12}:[A-Za-z0-9_-]{30,}$'; then
    echo "XATO: token shakli to'g'ri emas. @BotFather bergan qiymatni to'liq nusxalang." >&2
    echo "      Ko'rinishi: 1234567890:AAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" >&2
    exit 1
fi

# Telegram'dan botni so'raymiz — token qaysi botga tegishli ekanini u aytadi.
echo "Telegram'da tekshirilmoqda..."
RESPONSE="$(curl -s --max-time 20 "https://api.telegram.org/bot${TOKEN}/getMe" || true)"

if ! printf '%s' "$RESPONSE" | grep -q '"ok":true'; then
    echo "" >&2
    echo "XATO: Telegram bu tokenni qabul qilmadi. Javob:" >&2
    printf '%s\n' "$RESPONSE" | head -c 400 >&2
    echo "" >&2
    echo "Sabablari: token bekor qilingan (/revoke) yoki to'liq nusxalanmagan." >&2
    echo "Hech narsa o'zgartirilmadi." >&2
    exit 1
fi

USERNAME="$(printf '%s' "$RESPONSE" | sed -n 's/.*"username":"\([^"]*\)".*/\1/p')"
echo "OK — token @${USERNAME} botiga tegishli."

# .env ni yangilaymiz: BOT_TOKEN va BOT_USERNAME (token qaysi botga tegishli
# bo'lsa, username ham o'sha bo'lishi kerak — aks holda deep link va
# kanal postidagi "@bot" boshqa botga ishora qiladi).
# BOT_MODE ham yoqiladi: token bor, demak bot tinglashi kerak. Aks holda ilova
# ko'tariladi-yu, /start ga javob bermaydi (mode=off).
TMP="$(mktemp)"
awk -v token="$TOKEN" -v user="$USERNAME" '
    /^BOT_TOKEN=/    { print "BOT_TOKEN=" token; next }
    /^BOT_USERNAME=/ { print "BOT_USERNAME=" user; next }
    /^BOT_MODE=/     { print "BOT_MODE=polling"; next }
    { print }
' "$ENV_FILE" > "$TMP"
mv "$TMP" "$ENV_FILE"

echo ""
echo "Yozildi: .env"
echo "  BOT_TOKEN    = <${#TOKEN} belgi>"
echo "  BOT_USERNAME = ${USERNAME}"
echo "  BOT_MODE     = polling"
echo ""
echo "Keyingi qadam: Claude'ga \"tayyor\" deb yozing — backendni qayta ishga tushiradi."
