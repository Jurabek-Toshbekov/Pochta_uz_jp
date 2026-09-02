package uz.pochtajp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Post;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    /** Kunlik e'lon chegarasi uchun (§7.2 — 5 e'lon/kun). */
    long countByUser_IdAndCreatedAtAfterAndDeletedAtIsNull(UUID userId, Instant since);

    /** Profil ekrani: jami e'lonlar (§9.1). */
    long countByUser_IdAndDeletedAtIsNull(UUID userId);

    /** Profil ekrani: hozir kanalda turganlari. */
    long countByUser_IdAndStatusAndDeletedAtIsNull(UUID userId, uz.pochtajp.domain.enums.PostStatus status);

    /** "Mening e'lonlarim" — eng yangisi tepada. */
    List<Post> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /**
     * Ko'rish hisoblagichi. Ataylab JPQL UPDATE: entity yuklanmaydi va
     * {@code updated_at} tegilmaydi — e'lonni ko'rish uni tahrirlash emas.
     */
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.contactRevealCount = p.contactRevealCount + 1 WHERE p.id = :id")
    void incrementContactRevealCount(@Param("id") UUID id);

    /**
     * "Odam topdingizmi?" so'rovi uchun nomzodlar (§6.4, 1-band).
     *
     * <p>Publish bo'lganiga kamida N kun bo'lgan, hali yopilmagan va bitim
     * tasdiqlanmagan e'lonlar. Takroriy so'rov {@code notifications_sent}
     * dagi unikal indeks bilan to'siladi, shu sabab bu yerda tekshirilmaydi.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.status = uz.pochtajp.domain.enums.PostStatus.PUBLISHED
              AND p.dealConfirmedAt IS NULL
              AND p.publishedAt IS NOT NULL
              AND p.publishedAt < :before
            ORDER BY p.publishedAt ASC
            """)
    List<Post> findDealFollowUpCandidates(@Param("before") Instant before,
                                          org.springframework.data.domain.Pageable pageable);

    /**
     * Muddati tugashiga oz qolgan e'lonlar (§8.3).
     *
     * <p>Oraliq ataylab: allaqachon o'tib ketganlar ogohlantirilmaydi,
     * juda uzoqdagilar esa hali erta.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.status = uz.pochtajp.domain.enums.PostStatus.PUBLISHED
              AND p.expiresAt IS NOT NULL
              AND p.expiresAt BETWEEN :from AND :to
            ORDER BY p.expiresAt ASC
            """)
    List<Post> findExpiringSoon(@Param("from") Instant from,
                                @Param("to") Instant to,
                                org.springframework.data.domain.Pageable pageable);

    /** Ishonch balli uchun: muvaffaqiyatli yakunlangan e'lonlar soni. */
    @Query("""
            SELECT count(p) FROM Post p
            WHERE p.deletedAt IS NULL
              AND p.user.id = :userId
              AND p.dealConfirmedAt IS NOT NULL
            """)
    long countConfirmedDeals(@Param("userId") UUID userId);
}
