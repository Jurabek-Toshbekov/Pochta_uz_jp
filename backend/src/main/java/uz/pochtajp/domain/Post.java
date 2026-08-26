package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import uz.pochtajp.domain.enums.ClosedReason;
import uz.pochtajp.domain.enums.Currency;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.PostSource;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;

/**
 * E’lon. Hech qachon fizik o’chirilmaydi — status=DELETED va deleted_at (§1.1).
 */
@Entity
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corridor_id", nullable = false)
    private Corridor corridor;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 12)
    private PostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 12)
    private Direction direction;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "origin_airport")
    private Airport originAirport;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dest_airport")
    private Airport destAirport;

    /** Ro’yxatda yo’q bo’lsa. */
    @Column(name = "origin_city_free", length = 120)
    private String originCityFree;

    @Column(name = "dest_city_free", length = 120)
    private String destCityFree;

    @Column(name = "final_destination", length = 120)
    private String finalDestination;

    /** CARRY: uchish sanasi. */
    @Column(name = "depart_date")
    private LocalDate departDate;

    /** SEND: qachongacha kerak. */
    @Column(name = "deadline_date")
    private LocalDate deadlineDate;

    @Column(name = "date_flexible_days", nullable = false)
    private short dateFlexibleDays = 0;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "weight_kg_max", precision = 6, scale = 2)
    private BigDecimal weightKgMax;

    @Column(name = "price_amount", precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_currency", length = 3)
    private Currency priceCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit", length = 16)
    private PriceUnit priceUnit;

    @Column(name = "comment")
    private String comment;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "contact_telegram", length = 64)
    private String contactTelegram;

    @Column(name = "contact_other", length = 160)
    private String contactOther;

    /** §7.3 — belgilanmasa publish bo’lmaydi. */
    @Column(name = "safety_checklist_ok", nullable = false)
    private boolean safetyChecklistOk = false;

    @Column(name = "safety_checked_at")
    private Instant safetyCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "is_promoted", nullable = false)
    private boolean promoted = false;

    @Column(name = "promoted_until")
    private Instant promotedUntil;

    @Column(name = "channel_message_id")
    private Long channelMessageId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_reason", length = 32)
    private ClosedReason closedReason;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "contact_reveal_count", nullable = false)
    private int contactRevealCount = 0;

    @Column(name = "search_hit_count", nullable = false)
    private int searchHitCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private PostSource source = PostSource.MINIAPP;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Soft delete. Fizik DELETE taqiqlangan (§1.1). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Corridor getCorridor() {
        return corridor;
    }

    public void setCorridor(Corridor corridor) {
        this.corridor = corridor;
    }

    public PostType getPostType() {
        return postType;
    }

    public void setPostType(PostType postType) {
        this.postType = postType;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Airport getOriginAirport() {
        return originAirport;
    }

    public void setOriginAirport(Airport originAirport) {
        this.originAirport = originAirport;
    }

    public Airport getDestAirport() {
        return destAirport;
    }

    public void setDestAirport(Airport destAirport) {
        this.destAirport = destAirport;
    }

    public String getOriginCityFree() {
        return originCityFree;
    }

    public void setOriginCityFree(String originCityFree) {
        this.originCityFree = originCityFree;
    }

    public String getDestCityFree() {
        return destCityFree;
    }

    public void setDestCityFree(String destCityFree) {
        this.destCityFree = destCityFree;
    }

    public String getFinalDestination() {
        return finalDestination;
    }

    public void setFinalDestination(String finalDestination) {
        this.finalDestination = finalDestination;
    }

    public LocalDate getDepartDate() {
        return departDate;
    }

    public void setDepartDate(LocalDate departDate) {
        this.departDate = departDate;
    }

    public LocalDate getDeadlineDate() {
        return deadlineDate;
    }

    public void setDeadlineDate(LocalDate deadlineDate) {
        this.deadlineDate = deadlineDate;
    }

    public short getDateFlexibleDays() {
        return dateFlexibleDays;
    }

    public void setDateFlexibleDays(short dateFlexibleDays) {
        this.dateFlexibleDays = dateFlexibleDays;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getWeightKgMax() {
        return weightKgMax;
    }

    public void setWeightKgMax(BigDecimal weightKgMax) {
        this.weightKgMax = weightKgMax;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public void setPriceAmount(BigDecimal priceAmount) {
        this.priceAmount = priceAmount;
    }

    public Currency getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(Currency priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public PriceUnit getPriceUnit() {
        return priceUnit;
    }

    public void setPriceUnit(PriceUnit priceUnit) {
        this.priceUnit = priceUnit;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactTelegram() {
        return contactTelegram;
    }

    public void setContactTelegram(String contactTelegram) {
        this.contactTelegram = contactTelegram;
    }

    public String getContactOther() {
        return contactOther;
    }

    public void setContactOther(String contactOther) {
        this.contactOther = contactOther;
    }

    public boolean getSafetyChecklistOk() {
        return safetyChecklistOk;
    }

    public void setSafetyChecklistOk(boolean safetyChecklistOk) {
        this.safetyChecklistOk = safetyChecklistOk;
    }

    public Instant getSafetyCheckedAt() {
        return safetyCheckedAt;
    }

    public void setSafetyCheckedAt(Instant safetyCheckedAt) {
        this.safetyCheckedAt = safetyCheckedAt;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public boolean getPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    public Instant getPromotedUntil() {
        return promotedUntil;
    }

    public void setPromotedUntil(Instant promotedUntil) {
        this.promotedUntil = promotedUntil;
    }

    public Long getChannelMessageId() {
        return channelMessageId;
    }

    public void setChannelMessageId(Long channelMessageId) {
        this.channelMessageId = channelMessageId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ClosedReason getClosedReason() {
        return closedReason;
    }

    public void setClosedReason(ClosedReason closedReason) {
        this.closedReason = closedReason;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public int getContactRevealCount() {
        return contactRevealCount;
    }

    public void setContactRevealCount(int contactRevealCount) {
        this.contactRevealCount = contactRevealCount;
    }

    public int getSearchHitCount() {
        return searchHitCount;
    }

    public void setSearchHitCount(int searchHitCount) {
        this.searchHitCount = searchHitCount;
    }

    public PostSource getSource() {
        return source;
    }

    public void setSource(PostSource source) {
        this.source = source;
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
