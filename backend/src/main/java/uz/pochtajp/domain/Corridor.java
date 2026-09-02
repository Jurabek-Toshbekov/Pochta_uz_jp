package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Koridor — davlat juftligi (JP_UZ, KR_UZ...). Kelajakdagi kengayish uchun.
 */
@Entity
@Table(name = "corridors")
public class Corridor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Short id;

    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Column(name = "origin_country", nullable = false, length = 2)
    private String originCountry;

    @Column(name = "dest_country", nullable = false, length = 2)
    private String destCountry;

    @Column(name = "title_uz", nullable = false, length = 120)
    private String titleUz;

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

    public String getOriginCountry() {
        return originCountry;
    }

    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }

    public String getDestCountry() {
        return destCountry;
    }

    public void setDestCountry(String destCountry) {
        this.destCountry = destCountry;
    }

    public String getTitleUz() {
        return titleUz;
    }

    public void setTitleUz(String titleUz) {
        this.titleUz = titleUz;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
