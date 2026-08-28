package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uz.pochtajp.domain.enums.SettingType;

/**
 * Feature flag va ishga tushirish sozlamalari (§11.2 — /settings).
 *
 * <p>Qiymat JSONB'da saqlanadi: {@code true}, {@code 5} yoki {@code "matn"}.
 * Turi {@link SettingType} bilan aniqlanadi, shuning uchun admin panel
 * to'g'ri boshqaruv elementini (toggle / raqam / matn) ko'rsata oladi.
 *
 * <p>Sozlama <b>hech qachon o'chirilmaydi</b> (§1.1) — kerak bo'lmasa
 * {@code deleted_at} qo'yiladi va ro'yxatdan chiqadi.
 */
@Entity
@Table(name = "app_settings")
@EntityListeners(AuditingEntityListener.class)
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "setting_key", nullable = false, length = 64, updatable = false)
    private String settingKey;

    /**
     * JSONB ustuni xom JSON matni sifatida saqlanadi: {@code true}, {@code 5},
     * {@code "matn"}. Hibernate'ga qiymat turini taxmin qildirmaymiz —
     * serializatsiya {@code SettingsService}da, bitta joyda va aniq.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", nullable = false)
    private String valueJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingType valueType;

    @Column(name = "title_uz", nullable = false, length = 160)
    private String titleUz;

    @Column(name = "description_uz")
    private String descriptionUz;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public SettingType getValueType() {
        return valueType;
    }

    public void setValueType(SettingType valueType) {
        this.valueType = valueType;
    }

    public String getTitleUz() {
        return titleUz;
    }

    public void setTitleUz(String titleUz) {
        this.titleUz = titleUz;
    }

    public String getDescriptionUz() {
        return descriptionUz;
    }

    public void setDescriptionUz(String descriptionUz) {
        this.descriptionUz = descriptionUz;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
