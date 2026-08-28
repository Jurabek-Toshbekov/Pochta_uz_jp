package uz.pochtajp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.common.exception.NotFoundException;
import uz.pochtajp.common.exception.ValidationException;
import uz.pochtajp.domain.AppSetting;
import uz.pochtajp.domain.enums.SettingType;
import uz.pochtajp.repository.AppSettingRepository;

/**
 * Feature flag va ishga tushirish sozlamalari (§11.2 — /settings).
 *
 * <p>Har so'rovda bazaga bormaslik uchun kichik kesh: sozlamalar kamdan-kam
 * o'zgaradi, lekin ular publish oqimidek issiq yo'lda o'qiladi. Kesh
 * yangilanganda darhol tozalanadi, qolganida esa davriy yangilanadi —
 * bir nechta instansiya bo'lganda ham qiymat oxir-oqibat tarqaladi.
 *
 * <p>Qiymat bazada JSONB xom matn sifatida yotadi ({@code true}, {@code 5},
 * {@code "matn"}). Serializatsiya faqat shu yerda — Hibernate'ga qiymat
 * turini taxmin qildirish JSONB'da xatoga olib keladi.
 *
 * <p>Sozlama <b>o'chirilmaydi</b> (§1.1): kerak bo'lmasa {@code deleted_at}.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    /** Kesh yangilanish oralig'i (5 daqiqa). {@code @Scheduled} konstanta talab qiladi. */
    static final long CACHE_TTL_MS = 5L * 60 * 1000;

    private final AppSettingRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public SettingsService(AppSettingRepository repository,
                           AuditService auditService,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** Boolean feature flag. Sozlama yo'q bo'lsa — kelishilgan standart qiymat. */
    public boolean flag(String key, boolean fallback) {
        Object value = value(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    /** Butun sonli chegara. Noto'g'ri yoki manfiy qiymat — standart qiymatga qaytadi. */
    public int number(String key, int fallback) {
        Object value = value(key);
        if (value instanceof Number num && num.intValue() > 0) {
            return num.intValue();
        }
        return fallback;
    }

    private Object value(String key) {
        Object cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        return repository.findBySettingKeyAndDeletedAtIsNull(key)
                .map(setting -> {
                    Object parsed = parse(setting.getValueJson());
                    if (parsed != null) {
                        cache.put(key, parsed);
                    }
                    return parsed;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AppSetting> all() {
        return repository.findByDeletedAtIsNullOrderBySettingKeyAsc();
    }

    /** Bitta sozlamaning o'qilgan qiymati — admin javobini yasash uchun. */
    public Object readValue(AppSetting setting) {
        return parse(setting.getValueJson());
    }

    /**
     * Sozlamani o'zgartiradi.
     *
     * <p>Tur qat'iy tekshiriladi: {@code BOOLEAN} kalitiga son yozib
     * bo'lmaydi, aks holda feature flag jim buziladi.
     */
    @Transactional
    public AppSetting update(String key, Object rawValue, UUID actorId) {
        AppSetting setting = repository.findBySettingKeyAndDeletedAtIsNull(key)
                .orElseThrow(() -> new NotFoundException("Bunday sozlama yo'q."));

        Object coerced = coerce(setting.getValueType(), rawValue);
        Object previous = parse(setting.getValueJson());

        setting.setValueJson(write(coerced));
        setting.setUpdatedBy(actorId);
        AppSetting saved = repository.saveAndFlush(setting);

        cache.put(key, coerced);
        auditService.record(actorId, "SETTING_UPDATE", "SETTING", key,
                Map.of("from", String.valueOf(previous), "to", String.valueOf(coerced)));
        log.info("Sozlama o'zgartirildi: key={} actor_id={}", key, actorId);
        return saved;
    }

    private static Object coerce(SettingType type, Object rawValue) {
        if (rawValue == null) {
            throw new ValidationException("Qiymat bo'sh bo'lishi mumkin emas.",
                    Map.of("value", "Qiymat ko'rsatilmagan."));
        }
        return switch (type) {
            case BOOLEAN -> {
                if (rawValue instanceof Boolean bool) {
                    yield bool;
                }
                throw new ValidationException("Bu sozlama faqat ha/yo'q qiymat qabul qiladi.",
                        Map.of("value", "true yoki false bo'lishi kerak."));
            }
            case NUMBER -> {
                if (rawValue instanceof Number num) {
                    yield num.intValue();
                }
                throw new ValidationException("Bu sozlama faqat raqam qabul qiladi.",
                        Map.of("value", "Butun son bo'lishi kerak."));
            }
            case STRING -> Objects.toString(rawValue);
        };
    }

    private Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            // Buzilgan qiymat butun ilovani to'xtatmasin — standart qiymat ishlaydi.
            log.error("Sozlama qiymati o'qilmadi");
            return null;
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ValidationException("Qiymatni saqlab bo'lmadi.",
                    Map.of("value", "Qiymat formati noto'g'ri."));
        }
    }

    /** Boshqa instansiyada o'zgargan qiymat ham oxir-oqibat yetib keladi. */
    @Scheduled(fixedRate = CACHE_TTL_MS)
    void refreshCache() {
        cache.clear();
    }
}
