package uz.pochtajp.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.NotificationSubscription;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostType;

@Repository
public interface NotificationSubscriptionRepository
        extends JpaRepository<NotificationSubscription, UUID> {

    List<NotificationSubscription> findByUser_IdAndActiveTrueAndDeletedAtIsNull(UUID userId);

    /**
     * E'longa mos kelishi <b>mumkin</b> bo'lgan obunalar.
     *
     * <p>Bu yerda faqat arzon shartlar tekshiriladi: yo'nalish va e'lon turi
     * (obunada {@code null} bo'lsa — farqi yo'q). Aeroport, sana va
     * kategoriya solishtiruvi Java tomonda bo'ladi — ular massiv va
     * oraliq bilan ishlaydi va SQL'da o'qib bo'lmas holga keladi.
     *
     * <p>Obuna egasi e'lon egasining o'zi bo'lsa chiqarib tashlanadi:
     * o'z e'loni haqida xabar olish mantiqsiz.
     */
    @Query("""
            SELECT s FROM NotificationSubscription s
            WHERE s.active = TRUE AND s.deletedAt IS NULL
              AND s.user.id <> :ownerId
              AND (s.direction IS NULL OR s.direction = :direction)
              AND (s.postType IS NULL OR s.postType = :postType)
            """)
    List<NotificationSubscription> findCandidates(@Param("ownerId") UUID ownerId,
                                                  @Param("direction") Direction direction,
                                                  @Param("postType") PostType postType);
}
