package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.pochtajp.domain.enums.RiskLevel;

/**
 * Yuk kategoriyasi va xavf darajasi.
 */
@Entity
@Table(name = "cargo_categories")
public class CargoCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Short id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "title_uz", nullable = false, length = 80)
    private String titleUz;

    @Column(name = "title_ru", nullable = false, length = 80)
    private String titleRu;

    @Column(name = "emoji", length = 8)
    private String emoji;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 12)
    private RiskLevel riskLevel;

    /** HIGH risk bo’lsa publish’dan oldin ko’rsatiladi (§7.3). */
    @Column(name = "warning_uz")
    private String warningUz;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitleUz() {
        return titleUz;
    }

    public void setTitleUz(String titleUz) {
        this.titleUz = titleUz;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public void setTitleRu(String titleRu) {
        this.titleRu = titleRu;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getWarningUz() {
        return warningUz;
    }

    public void setWarningUz(String warningUz) {
        this.warningUz = warningUz;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
