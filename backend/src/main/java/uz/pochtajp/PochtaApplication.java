package uz.pochtajp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pochta JP-UZ backend.
 *
 * <p>Sxema Flyway migratsiyalari orqali boshqariladi, {@code ddl-auto=validate}
 * (CLAUDE.md §1.3) — Hibernate faqat entity va jadval mosligini tekshiradi.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class PochtaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PochtaApplication.class, args);
    }
}
