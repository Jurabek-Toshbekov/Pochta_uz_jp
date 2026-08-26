#!/usr/bin/env python3
"""
dev-initdata.py — FAQAT LOKAL SINOV UCHUN.

Telegram Mini App `initData` satrini yasaydi va bot tokeni bilan imzolaydi
(CLAUDE.md §7.1 algoritmi). Telegram ochmasdan API'ni sinash uchun kerak.

MUHIM: bu skriptni HAQIQIY bot tokeni bilan ishlatmang va natijasini
hech qayerga commit qilmang. Lokal sinovda soxta token ishlatiladi.

Ishlatish:
    python scripts/dev-initdata.py --token "123456789:LOCAL-TEST-ONLY-..." --user-id 900000042
    python scripts/dev-initdata.py --token "..." --user-id 900000042 --start-param ch_<postId>
"""

import argparse
import hashlib
import hmac
import json
import time
import urllib.parse


def build_init_data(token: str, user_id: int, first_name: str, username: str,
                    language_code: str, start_param: str | None) -> str:
    user = {
        "id": user_id,
        "first_name": first_name,
        "last_name": "Test",
        "username": username,
        "language_code": language_code,
        "is_premium": False,
    }
    fields = {
        "user": json.dumps(user, separators=(",", ":"), ensure_ascii=False),
        "auth_date": str(int(time.time())),
        "chat_type": "private",
    }
    if start_param:
        fields["start_param"] = start_param

    # data_check_string: kalitlar alifbo tartibida, \n bilan
    data_check_string = "\n".join(f"{key}={fields[key]}" for key in sorted(fields))

    secret_key = hmac.new(b"WebAppData", token.encode(), hashlib.sha256).digest()
    signature = hmac.new(secret_key, data_check_string.encode(), hashlib.sha256).hexdigest()

    query = urllib.parse.urlencode(fields, quote_via=urllib.parse.quote)
    return f"{query}&hash={signature}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Lokal sinov uchun initData yasaydi")
    parser.add_argument("--token", required=True, help="bot tokeni (lokal, soxta)")
    parser.add_argument("--user-id", type=int, default=900000042)
    parser.add_argument("--first-name", default="Lokal")
    parser.add_argument("--username", default="lokal_test")
    parser.add_argument("--language-code", default="uz")
    parser.add_argument("--start-param", default=None)
    args = parser.parse_args()

    print(build_init_data(args.token, args.user_id, args.first_name,
                          args.username, args.language_code, args.start_param))


if __name__ == "__main__":
    main()
