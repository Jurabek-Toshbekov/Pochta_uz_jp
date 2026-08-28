package uz.pochtajp.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Report;
import uz.pochtajp.domain.enums.ReportStatus;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    /** Takroriy shikoyat — metrikani buzadi va spam quroliga aylanadi. */
    boolean existsByPostIdAndReporterId(UUID postId, UUID reporterId);

    /** Ishonch balliga ta'sir qiladi: asosli shikoyatlar soni. */
    long countByReportedUserIdAndStatus(UUID reportedUserId, ReportStatus status);
}
