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
}
