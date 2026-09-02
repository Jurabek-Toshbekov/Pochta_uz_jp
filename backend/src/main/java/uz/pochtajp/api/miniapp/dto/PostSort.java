package uz.pochtajp.api.miniapp.dto;

/**
 * Qidiruv natijalarini saralash (§10.1).
 *
 * <p>Har bir tartib uchun keyset kaliti bor — offset ishlatilmaydi (§10.2).
 */
public enum PostSort {

    /** Eng yangi — default. Keyset: {@code (published_at, id)} DESC. */
    NEWEST("p.published_at", false, "timestamptz"),

    /** Uchish/muddat sanasi bo'yicha. Keyset: {@code (coalesce(depart_date, deadline_date), id)} ASC. */
    DEPART_DATE("coalesce(p.depart_date, p.deadline_date)", true, "date"),

    /**
     * Arzon. "Kelishamiz" e'lonlari oxirida turadi.
     *
     * <p>Diqqat: valyutalar aralash bo'lsa tartib taxminiy — valyuta kursi
     * jadvali 4-bosqichda qo'shiladi (docs/METRICS.md, Price index). Shu sababli
     * UI bu tartibni tanlaganda valyuta filtrini ham taklif qiladi.
     */
    CHEAPEST("coalesce(p.price_amount, 999999999999)", true, "numeric"),

    /** E'lon egasining ishonch balli. Keyset: {@code (trust_score, id)} DESC. */
    RATING("u.trust_score", false, "integer");

    private final String keyExpression;
    private final boolean ascending;
    private final String keyType;

    PostSort(String keyExpression, boolean ascending, String keyType) {
        this.keyExpression = keyExpression;
        this.ascending = ascending;
        this.keyType = keyType;
    }

    public String keyExpression() {
        return keyExpression;
    }

    public boolean ascending() {
        return ascending;
    }

    /** Kursor qiymatini SQL'da qaysi turga o'girish kerak. */
    public String keyType() {
        return keyType;
    }

    public String direction() {
        return ascending ? "ASC" : "DESC";
    }

    /** Keyset taqqoslash belgisi. */
    public String comparison() {
        return ascending ? ">" : "<";
    }
}
