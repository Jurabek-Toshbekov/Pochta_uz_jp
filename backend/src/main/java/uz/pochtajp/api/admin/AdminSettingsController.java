package uz.pochtajp.api.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.api.admin.dto.AdminRequests;
import uz.pochtajp.common.exception.ForbiddenException;
import uz.pochtajp.domain.AppSetting;
import uz.pochtajp.security.AdminPrincipal;
import uz.pochtajp.security.CurrentAdmin;
import uz.pochtajp.service.AuditService;
import uz.pochtajp.service.SettingsService;

/**
 * Sozlamalar va audit jurnali (§11.2 — /settings, /audit).
 *
 * <p>Sozlamani faqat {@code ADMIN} o'zgartira oladi: feature flag butun
 * mahsulot xatti-harakatini o'zgartiradi, moderator uchun bu ortiqcha
 * huquq. O'qish ikkalasiga ham ochiq.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {

    private final SettingsService settingsService;
    private final AuditService auditService;

    public AdminSettingsController(SettingsService settingsService, AuditService auditService) {
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    @GetMapping("/settings")
    public List<AdminDto.SettingRow> settings() {
        return settingsService.all().stream().map(this::toRow).toList();
    }

    @PatchMapping("/settings/{key}")
    public AdminDto.SettingRow updateSetting(@PathVariable String key,
                                             @Valid @RequestBody AdminRequests.UpdateSettingRequest request) {
        AdminPrincipal admin = CurrentAdmin.require();
        if (!admin.isAdmin()) {
            throw new ForbiddenException("Sozlamani faqat ADMIN o'zgartira oladi.");
        }
        return toRow(settingsService.update(key, request.value(), admin.userId()));
    }

    @GetMapping("/audit")
    public AdminDto.Page<AdminDto.AuditRow> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditService.list(action, actorId, page, size);
    }

    private AdminDto.SettingRow toRow(AppSetting setting) {
        return new AdminDto.SettingRow(
                setting.getSettingKey(),
                settingsService.readValue(setting),
                setting.getValueType().name(),
                setting.getTitleUz(),
                setting.getDescriptionUz(),
                setting.getUpdatedAt());
    }
}
