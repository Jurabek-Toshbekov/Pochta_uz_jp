package uz.pochtajp.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import uz.pochtajp.common.exception.ValidationException;

/**
 * Keyset kursori (§10.2 — "Keyset pagination, offset emas").
 *
 * <p>Nima uchun offset emas: yangi e'lon publish bo'lsa offset sahifalari
 * siljib ketadi va foydalanuvchi bir e'lonni ikki marta ko'radi yoki
 * butunlay o'tkazib yuboradi. Keyset "shu qiymatdan keyingisi" deb so'raydi.
 *
 * <p>Format: {@code base64url("<sortKey>|<id>")}. Ichida sir yo'q — faqat
 * saralash qiymati va oxirgi qatorning ID'si.
 *
 * @param key saralash ustunining qiymati (matn ko'rinishida; SQL'da tipiga o'giriladi)
 * @param id  oxirgi qatorning ID'si — bir xil kalitli qatorlarni ajratish uchun
 */
public record SearchCursor(String key, UUID id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = key + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<SearchCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(SEPARATOR);
            if (separator <= 0) {
                throw new IllegalArgumentException("separator");
            }
            String key = raw.substring(0, separator);
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return Optional.of(new SearchCursor(key, id));
        } catch (IllegalArgumentException ex) {
            // Buzilgan kursor — foydalanuvchi qo'lda o'zgartirgan yoki eski versiya.
            throw ValidationException.field("cursor", "Sahifa belgisi yaroqsiz. Qidiruvni qaytadan boshlang.");
        }
    }
}
