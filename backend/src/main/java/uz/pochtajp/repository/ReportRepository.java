package uz.pochtajp.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Report;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {
}
