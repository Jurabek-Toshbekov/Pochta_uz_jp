package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.ModerationActionLog;

@Repository
public interface ModerationActionLogRepository extends JpaRepository<ModerationActionLog, Long> {
}
