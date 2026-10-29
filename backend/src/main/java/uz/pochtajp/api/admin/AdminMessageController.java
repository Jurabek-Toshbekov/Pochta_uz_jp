package uz.pochtajp.api.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.api.admin.dto.AdminRequests;
import uz.pochtajp.security.CurrentAdmin;
import uz.pochtajp.service.AdminMessageService;

/**
 * Admin ↔ foydalanuvchi yozishmasi (bot orqali).
 *
 * <p>Yuboruvchi {@link CurrentAdmin} dan olinadi, so'rov tanasidan emas.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMessageController {

    private final AdminMessageService messageService;

    public AdminMessageController(AdminMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/users/{userId}/messages")
    public List<AdminDto.MessageRow> thread(@PathVariable UUID userId) {
        return messageService.thread(userId);
    }

    @PostMapping("/users/{userId}/messages")
    public AdminDto.MessageRow send(@PathVariable UUID userId,
                                    @Valid @RequestBody AdminRequests.SendMessageRequest request) {
        return messageService.send(userId, request.text(), CurrentAdmin.requireId());
    }

    @PostMapping("/users/{userId}/messages/read")
    public Map<String, Integer> markRead(@PathVariable UUID userId) {
        return Map.of("marked", messageService.markRead(userId));
    }

    @GetMapping("/messages")
    public List<AdminDto.Conversation> conversations() {
        return messageService.conversations();
    }

    @GetMapping("/messages/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", messageService.unreadCount());
    }
}
