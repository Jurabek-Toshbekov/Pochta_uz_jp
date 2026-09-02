package uz.pochtajp.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.pochtajp.domain.DailyMetric;

/** Kunlik agregatlar (§6.2). */
public interface DailyMetricRepository extends JpaRepository<DailyMetric, Long> {

    Optional<DailyMetric> findByMetricDateAndMetricKeyAndDimension(
            LocalDate metricDate, String metricKey, String dimension);

    List<DailyMetric> findByMetricKeyAndMetricDateBetweenOrderByMetricDateAsc(
            String metricKey, LocalDate from, LocalDate to);
}
