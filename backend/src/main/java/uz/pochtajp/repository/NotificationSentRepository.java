package uz.pochtajp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.NotificationSent;
import uz.pochtajp.domain.enums.NotificationKind;
import uz.pochtajp.domain.enums.NotificationStatus;

@Repository
public interface NotificationSentRepository extends JpaRepository<NotificationSent, Long> {

    /** Takroriy xabar yubormaslik uchun (§10.3). */
    boolean existsByPostIdAndUserIdAndKind(UUID postId, UUID userId, NotificationKind kind);

    /** Kunlik chegara: shu oynada nechta xabar ketgan. */
    long countByUserIdAndStatusAndCreatedAtAfter(UUID userId, NotificationStatus status, Instant since);

    /**
     * Digest navbati: yuborilishi kutayotgan xabarlar, eng eskisi oldin.
     *
     * <p>Foydalanuvchi kesimida guruhlash Java tomonda bo'ladi — bir odamning
     * bir necha e'loni bitta xabarga birlashadi.
     */
    @Query("""
            SELECT n FROM NotificationSent n
            WHERE n.status = uz.pochtajp.domain.enums.NotificationStatus.PENDING
              AND n.kind = uz.pochtajp.domain.enums.NotificationKind.MATCH
            ORDER BY n.createdAt ASC
            """)
    List<NotificationSent> findPendingQueue(org.springframework.data.domain.Pageable pageable);

    /** Deep link ochilganda ochilish vaqtini yozish uchun. */
    @Query("""
            SELECT n FROM NotificationSent n
            WHERE n.postId = :postId AND n.userId = :userId AND n.openedAt IS NULL
            ORDER BY n.createdAt DESC
            LIMIT 1
            """)
    Optional<NotificationSent> findLatestUnopened(@Param("postId") UUID postId,
                                                  @Param("userId") UUID userId);
}
