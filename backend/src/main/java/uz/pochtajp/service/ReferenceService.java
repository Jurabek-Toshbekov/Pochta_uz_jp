package uz.pochtajp.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.pochtajp.api.miniapp.dto.ReferenceResponse;
import uz.pochtajp.config.CacheConfig;
import uz.pochtajp.repository.AirportRepository;
import uz.pochtajp.repository.CargoCategoryRepository;
import uz.pochtajp.repository.CorridorRepository;

/**
 * Aeroport, kategoriya va koridor ro'yxatlari (§12 {@code GET /reference}).
 *
 * <p>Faqat {@code is_active = true} bo'lganlar qaytadi: nofaol aeroportni
 * o'chirmaymiz (§1.1), shunchaki ro'yxatdan chiqaramiz.
 */
@Service
public class ReferenceService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceService.class);
    private static final long CACHE_TTL_MS = 3_600_000L;

    private final AirportRepository airportRepository;
    private final CargoCategoryRepository cargoCategoryRepository;
    private final CorridorRepository corridorRepository;

    public ReferenceService(AirportRepository airportRepository,
                            CargoCategoryRepository cargoCategoryRepository,
                            CorridorRepository corridorRepository) {
        this.airportRepository = airportRepository;
        this.cargoCategoryRepository = cargoCategoryRepository;
        this.corridorRepository = corridorRepository;
    }

    @Cacheable(CacheConfig.REFERENCE_CACHE)
    @Transactional(readOnly = true)
    public ReferenceResponse load() {
        List<ReferenceResponse.AirportDto> airports =
                airportRepository.findByActiveTrueOrderByCountryCodeAscSortOrderAsc().stream()
                        .map(ReferenceResponse.AirportDto::from)
                        .toList();
        List<ReferenceResponse.CategoryDto> categories =
                cargoCategoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                        .map(ReferenceResponse.CategoryDto::from)
                        .toList();
        List<ReferenceResponse.CorridorDto> corridors =
                corridorRepository.findAll().stream()
                        .filter(corridor -> corridor.getActive())
                        .map(ReferenceResponse.CorridorDto::from)
                        .toList();

        log.debug("Reference kesh yangilandi: {} aeroport, {} kategoriya", airports.size(), categories.size());
        return new ReferenceResponse(airports, categories, corridors);
    }

    /** Soatda bir marta tozalanadi — admin reference'ni o'zgartirsa (§11.2) yetib boradi. */
    @Scheduled(fixedRate = CACHE_TTL_MS)
    @CacheEvict(value = CacheConfig.REFERENCE_CACHE, allEntries = true)
    public void evictCache() {
        log.debug("Reference kesh tozalandi");
    }
}
