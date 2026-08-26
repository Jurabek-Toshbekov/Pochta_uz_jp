package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.NotificationSent;

@Repository
public interface NotificationSentRepository extends JpaRepository<NotificationSent, Long> {
}
