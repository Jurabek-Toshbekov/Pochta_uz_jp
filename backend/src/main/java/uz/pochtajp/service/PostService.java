package uz.pochtajp.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.CreatePostRequest;
import uz.pochtajp.api.miniapp.dto.PostResponse;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.RateLimitException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.config.AppProperties;
import uz.pochtajp.config.BotProperties;
import uz.pochtajp.domain.Airport;
import uz.pochtajp.domain.CargoCategory;
import uz.pochtajp.domain.Corridor;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.PostCategory;
import uz.pochtajp.domain.User;
import uz.pochtajp.domain.enums.Direction;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.PostSource;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;
import uz.pochtajp.repository.AirportRepository;
import uz.pochtajp.repository.CargoCategoryRepository;
import uz.pochtajp.repository.CorridorRepository;
import uz.pochtajp.repository.PostCategoryRepository;
import uz.pochtajp.repository.PostRepository;
import uz.pochtajp.repository.UserRepository;
import uz.pochtajp.security.MiniAppPrincipal;

/**
 * E'lon yaratish va o'qish (§9.2 formasining server tomoni).
 *
 * <p>Bu klass faqat bazaga yozadi va tekshiradi. Kanalga yuborish
 * {@link PublishService}da — chunki tashqi API chaqiruvi tranzaksiya ichida
 * turmasligi kerak. Ikkalasini {@link PostSubmissionService} birlashtiradi.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    /** Bu bosqichda faqat bitta koridor bor; {@code corridors} jadvali kengayishga tayyor (§5.2). */
    private static final String DEFAULT_CORRIDOR = "JP_UZ";

    /** Sanani bir yildan uzoqqa qo'yish — deyarli har doim xato kiritish. */
    private static final int MAX_DAYS_AHEAD = 365;

    private final PostRepository postRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final UserRepository userRepository;
    private final AirportRepository airportRepository;
    private final CargoCategoryRepository cargoCategoryRepository;
    private final CorridorRepository corridorRepository;
    private final EventLogger eventLogger;
    private final AppProperties appProperties;
    private final BotProperties botProperties;

    public PostService(PostRepository postRepository,
                       PostCategoryRepository postCategoryRepository,
                       UserRepository userRepository,
                       AirportRepository airportRepository,
                       CargoCategoryRepository cargoCategoryRepository,
                       CorridorRepository corridorRepository,
                       EventLogger eventLogger,
                       AppProperties appProperties,
                       BotProperties botProperties) {
        this.postRepository = postRepository;
        this.postCategoryRepository = postCategoryRepository;
        this.userRepository = userRepository;
        this.airportRepository = airportRepository;
        this.cargoCategoryRepository = cargoCategoryRepository;
        this.corridorRepository = corridorRepository;
        this.eventLogger = eventLogger;
        this.appProperties = appProperties;
        this.botProperties = botProperties;
    }

    /**
     * E'lonni {@code PENDING} holatida yaratadi. Kanalga chiqarish keyingi qadam.
     *
     * @return yangi e'lonning ID'si
     */
    @Transactional
    public UUID createPending(CreatePostRequest request, MiniAppPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Foydalanuvchi topilmadi."));

        enforceDailyLimit(user);

        Airport origin = resolveAirport(request.originAirport(), "originAirport");
        Airport dest = resolveAirport(request.destAirport(), "destAirport");
        List<CargoCategory> categories = resolveCategories(request.distinctCategoryIds());
        validate(request, origin, dest);

        Corridor corridor = corridorRepository.findByCode(DEFAULT_CORRIDOR)
                .orElseThrow(() -> new IllegalStateException(
                        "Koridor topilmadi: " + DEFAULT_CORRIDOR + " (V2 seed qo'llanmagan?)"));

        Instant now = Instant.now();
        Post post = new Post();
        post.setUser(user);
        post.setCorridor(corridor);
        post.setPostType(request.postType());
        post.setDirection(request.direction());
        post.setOriginAirport(origin);
        post.setDestAirport(dest);
        post.setOriginCityFree(origin == null ? blankToNull(request.originCityFree()) : null);
        post.setDestCityFree(dest == null ? blankToNull(request.destCityFree()) : null);
        post.setFinalDestination(blankToNull(request.finalDestination()));
        post.setDepartDate(request.postType() == PostType.CARRY ? request.departDate() : null);
        post.setDeadlineDate(request.postType() == PostType.SEND ? request.deadlineDate() : null);
        post.setDateFlexibleDays((short) request.flexibleDaysOrZero());
        post.setWeightKg(request.weightKg());
        post.setWeightKgMax(request.weightKgMax());
        if (request.priceUnit() == PriceUnit.NEGOTIABLE) {
            // "Kelishamiz" — summa saqlanmaydi, aks holda narx indeksi buziladi (§6.3).
            post.setPriceAmount(null);
            post.setPriceCurrency(null);
        } else {
            post.setPriceAmount(request.priceAmount());
            post.setPriceCurrency(request.priceCurrency());
        }
        post.setPriceUnit(request.priceUnit());
        post.setComment(blankToNull(request.comment()));
        post.setContactPhone(blankToNull(request.contactPhone()));
        post.setContactTelegram(normalizeUsername(request.contactTelegram()));
        post.setContactOther(blankToNull(request.contactOther()));
        post.setSafetyChecklistOk(true);
        post.setSafetyCheckedAt(now);
        post.setStatus(PostStatus.PENDING);
        post.setSource(PostSource.MINIAPP);
        post.setExpiresAt(computeExpiry(request));

        postRepository.saveAndFlush(post);

        List<PostCategory> links = categories.stream()
                .map(category -> new PostCategory(post, category))
                .toList();
        postCategoryRepository.saveAll(links);

        log.info("E'lon yaratildi: post_id={} user_id={} type={} direction={}",
                post.getId(), user.getId(), post.getPostType(), post.getDirection());

        eventLogger.track(TrackedEvent.of(EventName.POST_SUBMIT, EventSource.MINIAPP)
                .user(user.getId())
                .session(request.sessionId())
                .post(post.getId())
                .platform(request.platform())
                .properties(submitProperties(request, post, categories))
                .build());

        return post.getId();
    }

    @Transactional(readOnly = true)
    public PostResponse getOwned(UUID postId, UUID ownerId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));
        if (!post.getUser().getId().equals(ownerId)) {
            // Boshqa odamning e'loni — mavjudligini ham oshkor qilmaymiz.
            throw new NotFoundException("E'lon topilmadi.");
        }
        return toResponse(post);
    }

    @Transactional(readOnly = true)
    public List<PostResponse> listOwn(UUID ownerId) {
        return postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    PostResponse toResponse(Post post) {
        List<Short> categoryIds = postCategoryRepository.findCategoryIdsByPostId(post.getId());
        return PostResponse.of(post, categoryIds,
                botProperties.channelUrlForMessage(post.getChannelMessageId()),
                botProperties.deepLinkForPost(post.getId()));
    }

    // ------------------------------------------------------------------
    // Tekshiruvlar
    // ------------------------------------------------------------------

    /** §7.2 — kuniga {@code RATE_LIMIT_POSTS_PER_DAY} e'lon. Xotiradan emas, bazadan hisoblanadi. */
    private void enforceDailyLimit(User user) {
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        long used = postRepository.countByUser_IdAndCreatedAtAfterAndDeletedAtIsNull(user.getId(), since);
        int limit = appProperties.rateLimitPostsPerDay();
        if (used >= limit) {
            log.warn("E'lon chegarasi: user_id={} used={} limit={}", user.getId(), used, limit);
            eventLogger.track(TrackedEvent.of(EventName.RATE_LIMIT_HIT, EventSource.MINIAPP)
                    .user(user.getId())
                    .property("endpoint", "POST /api/miniapp/posts")
                    .property("limit", limit)
                    .property("window_seconds", 86_400)
                    .build());
            throw new RateLimitException(
                    "Bir kunda " + limit + " tadan ko'p e'lon berilmaydi. Ertaga qayta urinib ko'ring.",
                    3_600L);
        }
    }

    private void validate(CreatePostRequest request, Airport origin, Airport dest) {
        ValidationException.Collector errors = new ValidationException.Collector();

        // §7.3 — checklist belgilanmasa publish bo'lmaydi.
        if (!Boolean.TRUE.equals(request.safetyChecklistOk())) {
            errors.add("safetyChecklistOk", "E'lon yuborish uchun xavfsizlik shartlarini tasdiqlang.");
        }

        validateDate(request, errors);
        validateRoute(request, origin, dest, errors);
        validatePrice(request, errors);
        validateWeight(request, errors);
        validateContact(request, errors);

        errors.throwIfAny();
    }

    private void validateDate(CreatePostRequest request, ValidationException.Collector errors) {
        boolean carry = request.postType() == PostType.CARRY;
        String field = carry ? "departDate" : "deadlineDate";
        LocalDate date = request.relevantDate();

        if (date == null) {
            errors.add(field, carry ? "Uchish sanasini tanlang." : "Oxirgi muddatni tanlang.");
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (date.isBefore(today)) {
            errors.add(field, "Sana o'tib ketgan. Bugundan keyingi kunni tanlang.");
        } else if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            errors.add(field, "Sana juda uzoqda. Bir yil ichidagi kunni tanlang.");
        }
    }

    private void validateRoute(CreatePostRequest request, Airport origin, Airport dest,
                               ValidationException.Collector errors) {
        if (origin == null && isBlank(request.originCityFree())) {
            errors.add("originAirport", "Chiqish aeroportini tanlang yoki shaharni yozing.");
        }
        if (dest == null && isBlank(request.destCityFree())) {
            errors.add("destAirport", "Kelish aeroportini tanlang yoki shaharni yozing.");
        }
        if (origin != null && dest != null && origin.getCode().equals(dest.getCode())) {
            errors.add("destAirport", "Chiqish va kelish aeroporti bir xil bo'lmaydi.");
        }
        // Yo'nalish va aeroport davlati mos kelishi shart — aks holda narx indeksi
        // va talab/taklif hisobi buziladi (§6.3).
        if (origin != null && dest != null) {
            String expectedOrigin = request.direction() == Direction.JP_UZ ? "JP" : "UZ";
            String expectedDest = request.direction() == Direction.JP_UZ ? "UZ" : "JP";
            if (!expectedOrigin.equals(origin.getCountryCode())) {
                errors.add("originAirport", "Tanlangan aeroport yo'nalishga mos kelmaydi.");
            }
            if (!expectedDest.equals(dest.getCountryCode())) {
                errors.add("destAirport", "Tanlangan aeroport yo'nalishga mos kelmaydi.");
            }
        }
    }

    private void validatePrice(CreatePostRequest request, ValidationException.Collector errors) {
        if (request.priceUnit() == PriceUnit.NEGOTIABLE) {
            return;
        }
        if (request.priceAmount() == null || request.priceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("priceAmount", "Narxni kiriting yoki \"Kelishamiz\"ni tanlang.");
        }
        if (request.priceCurrency() == null) {
            errors.add("priceCurrency", "Valyutani tanlang.");
        }
    }

    private void validateWeight(CreatePostRequest request, ValidationException.Collector errors) {
        if (request.weightKg() != null && request.weightKgMax() != null
                && request.weightKgMax().compareTo(request.weightKg()) < 0) {
            errors.add("weightKgMax", "Maksimal og'irlik minimaldan kichik bo'lmaydi.");
        }
    }

    private void validateContact(CreatePostRequest request, ValidationException.Collector errors) {
        boolean hasContact = !isBlank(request.contactPhone())
                || !isBlank(request.contactTelegram())
                || !isBlank(request.contactOther());
        if (!hasContact) {
            errors.add("contactTelegram", "Kamida bitta aloqa usulini qoldiring.");
        }
    }

    private Airport resolveAirport(String code, String field) {
        if (isBlank(code)) {
            return null;
        }
        Airport airport = airportRepository.findById(code.strip().toUpperCase())
                .orElseThrow(() -> ValidationException.field(field, "Bunday aeroport ro'yxatda yo'q."));
        if (!airport.getActive()) {
            throw ValidationException.field(field, "Bu aeroport hozir mavjud emas.");
        }
        return airport;
    }

    private List<CargoCategory> resolveCategories(Set<Short> ids) {
        if (ids.isEmpty()) {
            throw ValidationException.field("categoryIds", "Kamida bitta yuk turini tanlang.");
        }
        List<CargoCategory> found = cargoCategoryRepository.findAllById(ids);
        if (found.size() != ids.size()) {
            throw ValidationException.field("categoryIds", "Tanlangan yuk turi ro'yxatda yo'q.");
        }
        List<CargoCategory> inactive = found.stream().filter(category -> !category.getActive()).toList();
        if (!inactive.isEmpty()) {
            throw ValidationException.field("categoryIds", "Tanlangan yuk turi hozir mavjud emas.");
        }
        return found;
    }

    /** Muddat: tegishli sana + moslashuv kunlari + 1 kun (UTC kun oxiri). */
    private Instant computeExpiry(CreatePostRequest request) {
        LocalDate date = request.relevantDate();
        if (date == null) {
            return Instant.now().plus(30, ChronoUnit.DAYS);
        }
        return date.plusDays(request.flexibleDaysOrZero() + 1L)
                .atTime(LocalTime.MAX)
                .toInstant(ZoneOffset.UTC);
    }

    /** {@code post_submit} eventining xossalari (docs/EVENTS.md). */
    private Map<String, Object> submitProperties(CreatePostRequest request, Post post,
                                                 List<CargoCategory> categories) {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("post_type", post.getPostType().name());
        properties.put("direction", post.getDirection().name());
        properties.put("route", routeCode(post));
        properties.put("categories", categories.stream().map(CargoCategory::getCode).collect(Collectors.toList()));
        properties.put("price_unit", post.getPriceUnit().name());
        if (post.getPriceAmount() != null) {
            properties.put("price", post.getPriceAmount());
            properties.put("currency", post.getPriceCurrency().name());
        }
        if (post.getWeightKg() != null) {
            properties.put("weight_kg", post.getWeightKg());
        }
        LocalDate date = request.relevantDate();
        if (date != null) {
            properties.put("days_until_departure",
                    ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), date));
        }
        properties.put("date_flexible_days", request.flexibleDaysOrZero());
        properties.put("has_comment", post.getComment() != null);
        return properties;
    }

    private String routeCode(Post post) {
        List<String> parts = new ArrayList<>(2);
        parts.add(post.getOriginAirport() == null ? "FREE" : post.getOriginAirport().getCode());
        parts.add(post.getDestAirport() == null ? "FREE" : post.getDestAirport().getCode());
        return String.join("_", parts);
    }

    private static String normalizeUsername(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.replace("@", "");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
