package uz.pochtajp.api.miniapp.dto;

import java.util.UUID;

/**
 * Shikoyat va baho javoblari.
 *
 * @param id        yaratilgan yozuv (shikoyat uchun)
 * @param trustScore baholangan odamning yangi ishonch balli (baho uchun)
 * @param message   foydalanuvchiga ko'rsatiladigan matn
 */
public record TrustResponse(UUID id, Integer trustScore, String message) {

    public static TrustResponse reported(UUID id) {
        return new TrustResponse(id, null,
                "Shikoyat qabul qilindi. Moderator ko'rib chiqadi.");
    }

    public static TrustResponse reviewed(int trustScore) {
        return new TrustResponse(null, trustScore, "Rahmat, bahoyingiz yozildi.");
    }
}
