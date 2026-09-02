package uz.pochtajp.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.pochtajp.domain.AdminLoginCode;

/** Admin panelga kirish kodlari (§11.1). */
public interface AdminLoginCodeRepository extends JpaRepository<AdminLoginCode, UUID> {

    /** Ishlatilmagan va o'chirilmagan kodni hash bo'yicha topadi. */
    Optional<AdminLoginCode> findFirstByCodeHashAndUsedAtIsNullAndDeletedAtIsNull(String codeHash);

    /** Kod so'rash chastotasini cheklash uchun (§7.2). */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
