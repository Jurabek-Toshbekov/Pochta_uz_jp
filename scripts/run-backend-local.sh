#!/usr/bin/env bash
# =====================================================================
# Backendni .env fayli bilan ishga tushiradi — FAQAT LOKAL SINOV UCHUN.
#
# Nima uchun kerak: Spring Boot .env faylini o'zi o'qimaydi, qiymatlar
# environment variable bo'lishi kerak. Bu skript ularni o'qib beradi va
# Windows'dagi CRLF qatorlarini ham to'g'ri hal qiladi (aks holda token
# oxirida \r qolib, Telegram 401 qaytaradi).
#
# Ishlatish (loyiha ildizidan):
#   bash scripts/run-backend-local.sh
#
# To'xtatish: Ctrl+C
# =====================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env"
JAR=$(ls "$ROOT"/backend/target/*.jar 2>/dev/null | head -1)

if [ ! -f "$ENV_FILE" ]; then
    echo "XATO: $ENV_FILE topilmadi. .env.example dan nusxa oling." >&2
    exit 1
fi
if [ -z "$JAR" ]; then
    echo "XATO: jar topilmadi. Avval: cd backend && ./mvnw -DskipTests package" >&2
    exit 1
fi

# .env ni o'qish: izohlar tashlanadi, CR olib tashlanadi, qiymat ichidagi
# bo'shliqlar saqlanadi.
set -a
while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in
        ''|\#*) continue ;;
        *=*) ;;
        *) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    export "$key=$value"
done < "$ENV_FILE"
set +a

if [ -z "${BOT_TOKEN:-}" ]; then
    echo "OGOHLANTIRISH: BOT_TOKEN bo'sh — bot ishga tushmaydi, API ishlaydi." >&2
fi

echo "Backend ishga tushirilmoqda:"
echo "  jar        : $(basename "$JAR")"
echo "  DB         : ${DB_URL:-?}"
echo "  BOT_MODE   : ${BOT_MODE:-off}"
echo "  MINIAPP_URL: ${MINIAPP_URL:-<bo'sh>}"
echo "  token      : $([ -n "${BOT_TOKEN:-}" ] && echo "to'ldirilgan (${#BOT_TOKEN} belgi)" || echo "bo'sh")"
echo ""

exec java -jar "$JAR"
