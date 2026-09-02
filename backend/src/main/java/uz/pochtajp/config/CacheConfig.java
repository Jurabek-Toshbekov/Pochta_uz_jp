package uz.pochtajp.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reference ma'lumot keshi (§12 — "cache 1 soat").
 *
 * <p>Aeroportlar va kategoriyalar kuniga bir marta ham o'zgarmaydi, lekin har bir
 * Mini App ochilishida so'raladi. Xotiradagi kesh yetarli — tashqi kesh
 * (Redis) kiritish bu bosqichda ortiqcha murakkablik (§15).
 *
 * <p>Muddat {@link uz.pochtajp.service.ReferenceService} dagi rejalashtirilgan
 * tozalash bilan boshqariladi.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String REFERENCE_CACHE = "reference";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(REFERENCE_CACHE);
    }
}
