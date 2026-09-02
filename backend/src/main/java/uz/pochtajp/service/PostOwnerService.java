package uz.pochtajp.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.analytics.EventLogger;
import uz.pochtajp.analytics.EventName;
import uz.pochtajp.analytics.TrackedEvent;
import uz.pochtajp.api.miniapp.dto.ClosePostRequest;
import uz.pochtajp.api.miniapp.dto.PostResponse;
import uz.pochtajp.api.miniapp.dto.UpdatePostRequest;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.CargoCategory;
import uz.pochtajp.domain.Post;
import uz.pochtajp.domain.PostCategory;
import uz.pochtajp.domain.enums.EventSource;
import uz.pochtajp.domain.enums.PostStatus;
import uz.pochtajp.domain.enums.PostType;
import uz.pochtajp.domain.enums.PriceUnit;
import uz.pochtajp.repository.CargoCategoryRepository;
import uz.pochtajp.repository.PostCategoryRepository;
import uz.pochtajp.repository.PostRepository;

/**
 * E'lon egasining o'z e'loni ustidagi harakatlari: <b>tahrirlash</b> va
 * <b>yopish</b> (§9.1, §12).
 *
 * <p>Nima uchun alohida klass: {@link PostService} e'lon yaratish va o'qishga
 * javob beradi, bu yerda esa boshqa masala — mavjud e'lonni o'zgartirish va
 * o'zgarishni kanalga yetkazish.
 *
 * <p>Yopish mantiqi bu yerda takrorlanmaydi: u {@link DealService}ga
 * o'tkaziladi, chunki bot "Odam topdingizmi?" savoliga javobda aynan shu
 * ishni qiladi. Ikki yo'lda ikki xil mantiq bo'lsa fill rate metrikasi
 * ikkiga bo'linib ketadi (§6.3).
 */
@Service
public class PostOwnerService {

    private static final Logger log = LoggerFactory.getLogger(PostOwnerService.class);

    /** Sanani bir yildan uzoqqa qo'yish — deyarli har doim xato kiritish. */
    private static final int MAX_DAYS_AHEAD = 365;

    private final PostRepository postRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final CargoCategoryRepository cargoCategoryRepository;
    private final PostService postService;
    private final DealService dealService;
    private final ChannelPostFormatter formatter;
    private final ChannelPublisher channelPublisher;
    private final EventLogger eventLogger;

    public PostOwnerService(PostRepository postRepository,
                            PostCategoryRepository postCategoryRepository,
                            CargoCategoryRepository cargoCategoryRepository,
                            PostService postService,
                            DealService dealService,
                            ChannelPostFormatter formatter,
                            ChannelPublisher channelPublisher,
                            EventLogger eventLogger) {
        this.postRepository = postRepository;
        this.postCategoryRepository = postCategoryRepository;
        this.cargoCategoryRepository = cargoCategoryRepository;
        this.postService = postService;
        this.dealService = dealService;
        this.formatter = formatter;
        this.channelPublisher = channelPublisher;
        this.eventLogger = eventLogger;
    }

    // ------------------------------------------------------------------
    // Yopish (§6.4, 3-band)
    // ------------------------------------------------------------------

    /**
     * E'lonni sabab bilan yopadi.
     *
     * <p>{@code FOUND} bo'lsa bitim tasdiqlanadi: ishonch balli qayta
     * hisoblanadi va sherik aniq bo'lsa undan baho so'raladi — bot orqali
     * javob berilgandagi bilan bir xil.
     */
    @Transactional
    public PostResponse close(UUID postId, UUID ownerId, ClosePostRequest request) {
        Post post = requireOwned(postId, ownerId);
        if (post.getStatus() == PostStatus.CLOSED) {
            // Ikki marta yopish xato emas: foydalanuvchi tugmani ikki marta
            // bosgan bo'lishi mumkin. Holat o'zgarmaydi, event ham qo'shilmaydi.
            return postService.toResponse(post);
        }

        boolean askReview = dealService.answer(
                postId, ownerId, request.reason().toAnswer(), EventSource.MINIAPP);
        if (askReview) {
            dealService.askForReview(postId);
        }

        log.info("E'lon yopildi: post_id={} user_id={} reason={}", postId, ownerId, request.reason());
        return postService.toResponse(requireOwned(postId, ownerId));
    }

    // ------------------------------------------------------------------
    // Tahrirlash (§12 — PATCH /posts/{id})
    // ------------------------------------------------------------------

    /**
     * O'z e'lonini tahrirlaydi va kanaldagi postni yangilaydi.
     *
     * <p>Yopilgan yoki rad etilgan e'lon tahrirlanmaydi: u endi hech kimga
     * ko'rinmaydi, tahrir esa o'zgarish tarixini chalkashtiradi.
     */
    @Transactional
    public PostResponse update(UUID postId, UUID ownerId, UpdatePostRequest request) {
        Post post = requireOwned(postId, ownerId);
        if (post.getStatus() != PostStatus.PUBLISHED && post.getStatus() != PostStatus.PENDING) {
            throw new ForbiddenException("Yopilgan e'lonni tahrirlab bo'lmaydi. Yangi e'lon bering.");
        }

        List<String> changed = new ArrayList<>();
        applyText(post, request, changed);
        applyDates(post, request, changed);
        applyWeight(post, request, changed);
        applyPrice(post, request, changed);
        List<CargoCategory> categories = applyCategories(post, request, changed);

        if (changed.isEmpty()) {
            return postService.toResponse(post);
        }
        validate(post);
        // Kanaldagi postdagi "Tahrirlangan" belgisi shu maydonga qarab qo'yiladi.
        // Bu yergacha faqat haqiqiy o'zgarish bo'lganda kelinadi — `changed`
        // bo'sh bo'lsa yuqorida qaytib ketilgan.
        post.setEditedAt(Instant.now());
        postRepository.saveAndFlush(post);

        syncChannel(post, categories);

        eventLogger.track(TrackedEvent.of(EventName.POST_EDIT, EventSource.MINIAPP)
                .user(ownerId)
                .session(request.sessionId())
                .post(postId)
                .platform(request.platform())
                .property("changed_fields", changed)
                .build());
        log.info("E'lon tahrirlandi: post_id={} user_id={} fields={}", postId, ownerId, changed);

        return postService.toResponse(post);
    }

    // ------------------------------------------------------------------
    // Maydonlar
    // ------------------------------------------------------------------

    private void applyText(Post post, UpdatePostRequest request, List<String> changed) {
        if (request.comment() != null) {
            setIfChanged(post.getComment(), blankToNull(request.comment()), "comment", changed,
                    post::setComment);
        }
        if (request.finalDestination() != null) {
            setIfChanged(post.getFinalDestination(), blankToNull(request.finalDestination()),
                    "finalDestination", changed, post::setFinalDestination);
        }
        if (request.contactPhone() != null) {
            setIfChanged(post.getContactPhone(), blankToNull(request.contactPhone()),
                    "contactPhone", changed, post::setContactPhone);
        }
        if (request.contactTelegram() != null) {
            setIfChanged(post.getContactTelegram(), normalizeUsername(request.contactTelegram()),
                    "contactTelegram", changed, post::setContactTelegram);
        }
        if (request.contactOther() != null) {
            setIfChanged(post.getContactOther(), blankToNull(request.contactOther()),
                    "contactOther", changed, post::setContactOther);
        }
    }

    private void applyDates(Post post, UpdatePostRequest request, List<String> changed) {
        boolean carry = post.getPostType() == PostType.CARRY;
        if (carry && request.departDate() != null) {
            setIfChanged(post.getDepartDate(), request.departDate(), "departDate", changed,
                    post::setDepartDate);
        }
        if (!carry && request.deadlineDate() != null) {
            setIfChanged(post.getDeadlineDate(), request.deadlineDate(), "deadlineDate", changed,
                    post::setDeadlineDate);
        }
        if (request.dateFlexibleDays() != null) {
            short value = request.dateFlexibleDays().shortValue();
            if (post.getDateFlexibleDays() != value) {
                post.setDateFlexibleDays(value);
                changed.add("dateFlexibleDays");
            }
        }
        // Sana yoki moslashuv o'zgargan bo'lsa muddat ham qayta hisoblanadi:
        // aks holda e'lon uchish sanasidan oldin "eskirgan" bo'lib qoladi.
        if (changed.contains("departDate") || changed.contains("deadlineDate")
                || changed.contains("dateFlexibleDays")) {
            post.setExpiresAt(computeExpiry(post));
        }
    }

    private void applyWeight(Post post, UpdatePostRequest request, List<String> changed) {
        if (request.weightKg() != null) {
            setIfChanged(post.getWeightKg(), request.weightKg(), "weightKg", changed, post::setWeightKg);
        }
        if (request.weightKgMax() != null) {
            setIfChanged(post.getWeightKgMax(), request.weightKgMax(), "weightKgMax", changed,
                    post::setWeightKgMax);
        }
    }

    private void applyPrice(Post post, UpdatePostRequest request, List<String> changed) {
        PriceUnit unit = request.priceUnit() == null ? post.getPriceUnit() : request.priceUnit();
        if (request.priceUnit() != null && request.priceUnit() != post.getPriceUnit()) {
            post.setPriceUnit(request.priceUnit());
            changed.add("priceUnit");
        }
        if (unit == PriceUnit.NEGOTIABLE) {
            // "Kelishamiz" — summa saqlanmaydi, aks holda narx indeksi buziladi (§6.3).
            if (post.getPriceAmount() != null || post.getPriceCurrency() != null) {
                post.setPriceAmount(null);
                post.setPriceCurrency(null);
                changed.add("priceAmount");
            }
            return;
        }
        if (request.priceAmount() != null) {
            setIfChanged(post.getPriceAmount(), request.priceAmount(), "priceAmount", changed,
                    post::setPriceAmount);
        }
        if (request.priceCurrency() != null && request.priceCurrency() != post.getPriceCurrency()) {
            post.setPriceCurrency(request.priceCurrency());
            changed.add("priceCurrency");
        }
    }

    /** @return kanal matni uchun kategoriyalar (o'zgarmagan bo'lsa ham) */
    private List<CargoCategory> applyCategories(Post post, UpdatePostRequest request,
                                                List<String> changed) {
        Set<Short> requested = request.distinctCategoryIds();
        if (requested == null) {
            return currentCategories(post.getId());
        }
        if (requested.isEmpty()) {
            throw ValidationException.field("categoryIds", "Kamida bitta yuk turini tanlang.");
        }
        List<CargoCategory> found = cargoCategoryRepository.findAllById(requested);
        if (found.size() != requested.size()) {
            throw ValidationException.field("categoryIds", "Tanlangan yuk turi ro'yxatda yo'q.");
        }
        if (found.stream().anyMatch(category -> !category.getActive())) {
            throw ValidationException.field("categoryIds", "Tanlangan yuk turi hozir mavjud emas.");
        }

        Set<Short> current = Set.copyOf(postCategoryRepository.findCategoryIdsByPostId(post.getId()));
        if (current.equals(requested)) {
            return currentCategories(post.getId());
        }

        // Faqat farq o'zgartiriladi: qolganini o'chirib qayta qo'shish bir
        // tranzaksiya ichida bir xil birlamchi kalit bilan to'qnashadi.
        Set<Short> removed = current.stream()
                .filter(id -> !requested.contains(id))
                .collect(java.util.stream.Collectors.toSet());
        if (!removed.isEmpty()) {
            postCategoryRepository.deleteByPost_IdAndCategory_IdIn(post.getId(), removed);
        }
        List<PostCategory> added = found.stream()
                .filter(category -> !current.contains(category.getId()))
                .map(category -> new PostCategory(post, category))
                .toList();
        if (!added.isEmpty()) {
            postCategoryRepository.saveAll(added);
        }
        changed.add("categoryIds");
        return sorted(found);
    }

    private List<CargoCategory> currentCategories(UUID postId) {
        return sorted(postCategoryRepository.findWithCategoryByPostIds(List.of(postId)).stream()
                .map(PostCategory::getCategory)
                .toList());
    }

    private static List<CargoCategory> sorted(List<CargoCategory> categories) {
        return categories.stream()
                .sorted(Comparator.comparingInt(CargoCategory::getSortOrder))
                .toList();
    }

    // ------------------------------------------------------------------
    // Tekshiruv va kanal
    // ------------------------------------------------------------------

    /** Tahrirdan keyingi holat yaratishdagi qoidalarga mos kelishi kerak. */
    private void validate(Post post) {
        ValidationException.Collector errors = new ValidationException.Collector();

        boolean carry = post.getPostType() == PostType.CARRY;
        LocalDate date = carry ? post.getDepartDate() : post.getDeadlineDate();
        String dateField = carry ? "departDate" : "deadlineDate";
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (date == null) {
            errors.add(dateField, carry ? "Uchish sanasini tanlang." : "Oxirgi muddatni tanlang.");
        } else if (date.isBefore(today)) {
            errors.add(dateField, "Sana o'tib ketgan. Bugundan keyingi kunni tanlang.");
        } else if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            errors.add(dateField, "Sana juda uzoqda. Bir yil ichidagi kunni tanlang.");
        }

        if (post.getPriceUnit() != PriceUnit.NEGOTIABLE) {
            if (post.getPriceAmount() == null || post.getPriceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("priceAmount", "Narxni kiriting yoki \"Kelishamiz\"ni tanlang.");
            }
            if (post.getPriceCurrency() == null) {
                errors.add("priceCurrency", "Valyutani tanlang.");
            }
        }

        if (post.getWeightKg() != null && post.getWeightKgMax() != null
                && post.getWeightKgMax().compareTo(post.getWeightKg()) < 0) {
            errors.add("weightKgMax", "Maksimal og'irlik minimaldan kichik bo'lmaydi.");
        }

        // Yaratishdagi qoida bilan bir xil: ikkalasi ham qolishi kerak.
        // Tahrir orqali kontaktni yarim qoldirib ketish yo'li ochiq bo'lsa,
        // qoida amalda ishlamaydi.
        if (!notBlank(post.getContactTelegram())) {
            errors.add("contactTelegram", "Telegram username'ingizni yozing.");
        }
        if (!notBlank(post.getContactPhone())) {
            errors.add("contactPhone", "Telefon raqamingizni yozing.");
        }

        errors.throwIfAny();
    }

    /**
     * Kanaldagi postni yangilaydi.
     *
     * <p>Xato bo'lsa tahrir bekor qilinmaydi: bazadagi ma'lumot to'g'ri
     * bo'lishi muhimroq, kanal esa keyingi tahrirda yoki qo'lda tuzatiladi.
     * Aks holda Telegram vaqtincha javob bermaganda foydalanuvchi o'z
     * e'lonini umuman tahrirlay olmasdi.
     */
    private void syncChannel(Post post, List<CargoCategory> categories) {
        Long messageId = post.getChannelMessageId();
        if (messageId == null || post.getStatus() != PostStatus.PUBLISHED) {
            return;
        }
        try {
            channelPublisher.editChannelMessage(messageId, formatter.format(post, categories));
        } catch (ChannelPublisher.ChannelPublishException ex) {
            log.warn("Kanaldagi post yangilanmadi: post_id={} error_code={}",
                    post.getId(), ex.getErrorCode());
        }
    }

    private Post requireOwned(UUID postId, UUID ownerId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("E'lon topilmadi."));
        if (!post.getUser().getId().equals(ownerId)) {
            // Begona e'lon — mavjudligini ham oshkor qilmaymiz.
            throw new NotFoundException("E'lon topilmadi.");
        }
        return post;
    }

    private java.time.Instant computeExpiry(Post post) {
        LocalDate date = post.getPostType() == PostType.CARRY
                ? post.getDepartDate() : post.getDeadlineDate();
        if (date == null) {
            return java.time.Instant.now().plus(30, ChronoUnit.DAYS);
        }
        return date.plusDays(post.getDateFlexibleDays() + 1L)
                .atTime(LocalTime.MAX)
                .toInstant(ZoneOffset.UTC);
    }

    private static <T> void setIfChanged(T current, T next, String field, List<String> changed,
                                         java.util.function.Consumer<T> setter) {
        if (java.util.Objects.equals(current, next)) {
            return;
        }
        if (current instanceof BigDecimal a && next instanceof BigDecimal b && a.compareTo(b) == 0) {
            // 2000 va 2000.00 — bir xil narx, tahrir hisoblanmaydi.
            return;
        }
        setter.accept(next);
        changed.add(field);
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

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
