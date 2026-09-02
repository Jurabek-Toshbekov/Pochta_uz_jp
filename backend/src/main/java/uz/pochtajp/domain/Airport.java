package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Aeroport — IATA kodi bilan. E’lon erkin matnga emas, shu kodga bog’lanadi (§5.1).
 */
@Entity
@Table(name = "airports")
public class Airport {

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 4)
    private String code;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "city_uz", nullable = false, length = 80)
    private String cityUz;

    @Column(name = "city_ru", nullable = false, length = 80)
    private String cityRu;

    @Column(name = "city_en", nullable = false, length = 80)
    private String cityEn;

    @Column(name = "name_en", nullable = false, length = 160)
    private String nameEn;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;


    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_popular", nullable = false)
    private boolean popular = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCityUz() {
        return cityUz;
    }

    public void setCityUz(String cityUz) {
        this.cityUz = cityUz;
    }

    public String getCityRu() {
        return cityRu;
    }

    public void setCityRu(String cityRu) {
        this.cityRu = cityRu;
    }

    public String getCityEn() {
        return cityEn;
    }

    public void setCityEn(String cityEn) {
        this.cityEn = cityEn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public boolean getPopular() {
        return popular;
    }

    public void setPopular(boolean popular) {
        this.popular = popular;
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
