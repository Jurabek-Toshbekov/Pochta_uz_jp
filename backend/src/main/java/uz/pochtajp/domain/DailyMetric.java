package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Kunlik agregat (§6.2 — retention).
 *
 * <p>Raw event'lar 24 oydan keyin tozalanishi mumkin, bu jadval esa
 * abadiy qoladi. Shuning uchun dashboard'ning uzoq muddatli grafiklari
 * shu yerdan o'qiydi.
 *
 * <p>Kalit: {@code (metric_date, metric_key, dimension)}. {@code dimension}
 * bo'sh satr bo'lsa — umumiy qiymat, aks holda kesim (masalan {@code JP_UZ}).
 */
@Entity
@Table(name = "daily_metrics")
public class DailyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey;

    @Column(name = "dimension", nullable = false, length = 64)
    private String dimension = "";

    @Column(name = "value", nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public void setMetricDate(LocalDate metricDate) {
        this.metricDate = metricDate;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
