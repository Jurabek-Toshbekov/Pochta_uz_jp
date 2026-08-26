package uz.pochtajp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.NotificationSubscription;

@Repository
public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, UUID> {
    List<NotificationSubscription> findByUser_IdAndActiveTrueAndDeletedAtIsNull(UUID userId);
}
