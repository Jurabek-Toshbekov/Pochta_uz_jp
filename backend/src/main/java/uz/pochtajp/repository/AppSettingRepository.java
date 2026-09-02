package uz.pochtajp.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.pochtajp.domain.AppSetting;

/** Feature flag va sozlamalar (§11.2). */
public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {

    List<AppSetting> findByDeletedAtIsNullOrderBySettingKeyAsc();

    Optional<AppSetting> findBySettingKeyAndDeletedAtIsNull(String settingKey);
}
