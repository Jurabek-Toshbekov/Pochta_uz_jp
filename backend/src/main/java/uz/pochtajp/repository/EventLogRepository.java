package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.EventLog;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {
}
