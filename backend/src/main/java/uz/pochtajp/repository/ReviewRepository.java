package uz.pochtajp.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /** Bitta e'longa bitta odam faqat bir marta baho beradi. */
    boolean existsByPostIdAndAuthorIdAndDeletedAtIsNull(UUID postId, UUID authorId);

    List<Review> findBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId);

    /**
     * O'rtacha baho — ishonch ballini hisoblash uchun.
     *
     * <p>Ataylab ikkita alohida so'rov, bitta {@code Object[]} proyeksiya emas:
     * Spring Data ko'p ustunli natijani {@code Object[]} ga o'rab qaytaradi va
     * uzunlik kutilganidan farq qiladi. Natijada baho jim yo'qoladi va ball
     * noto'g'ri chiqadi — bu xato allaqachon bir marta sodir bo'lgan.
     */
    @Query("""
            SELECT coalesce(avg(r.rating), 0)
            FROM Review r
            WHERE r.subjectId = :subjectId AND r.deletedAt IS NULL
            """)
    double averageRating(@Param("subjectId") UUID subjectId);

    long countBySubjectIdAndDeletedAtIsNull(UUID subjectId);
}
