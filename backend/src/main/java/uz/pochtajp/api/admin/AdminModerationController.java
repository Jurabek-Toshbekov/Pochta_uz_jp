package uz.pochtajp.api.admin;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.pochtajp.api.admin.dto.AdminDto;
import uz.pochtajp.api.admin.dto.AdminRequests;
import uz.pochtajp.security.CurrentAdmin;
import uz.pochtajp.service.AdminPostService;
import uz.pochtajp.service.AdminReportService;
import uz.pochtajp.service.AdminUserService;

/**
 * Moderatsiya: e'lonlar, foydalanuvchilar, shikoyatlar (§11.2, §12).
 *
 * <p>Controller'da mantiq yo'q (§14) — faqat parametrlarni yig'ib
 * servisga uzatadi. Harakat qiluvchi shaxs {@link CurrentAdmin} dan
 * olinadi, so'rov tanasidan emas.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminModerationController {

    private final AdminPostService postService;
    private final AdminUserService userService;
    private final AdminReportService reportService;

    public AdminModerationController(AdminPostService postService,
                                     AdminUserService userService,
                                     AdminReportService reportService) {
        this.postService = postService;
        this.userService = userService;
        this.reportService = reportService;
    }

    // ------------------------------------------------------------------
    // E'lonlar
    // ------------------------------------------------------------------

    @GetMapping("/posts")
    public AdminDto.Page<AdminDto.PostRow> posts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "true") boolean priorityFirst,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return postService.list(new AdminPostService.Filter(
                status, type, direction, search, priorityFirst, page, size));
    }

    @GetMapping("/posts/{postId}")
    public AdminDto.PostDetail post(@PathVariable UUID postId) {
        return postService.detail(postId);
    }

    @PostMapping("/posts/{postId}/approve")
    public AdminDto.PostDetail approve(@PathVariable UUID postId) {
        return postService.approve(postId, CurrentAdmin.requireId());
    }

    @PostMapping("/posts/{postId}/reject")
    public AdminDto.PostDetail reject(@PathVariable UUID postId,
                                      @Valid @RequestBody AdminRequests.RejectRequest request) {
        return postService.reject(postId, request.reason(), CurrentAdmin.requireId());
    }

    @PatchMapping("/posts/{postId}")
    public AdminDto.PostDetail updatePost(@PathVariable UUID postId,
                                          @Valid @RequestBody AdminRequests.UpdatePostRequest request) {
        return postService.update(postId, request, CurrentAdmin.requireId());
    }

    @PostMapping("/posts/{postId}/close")
    public AdminDto.PostDetail closePost(@PathVariable UUID postId) {
        return postService.close(postId, CurrentAdmin.requireId());
    }

    // ------------------------------------------------------------------
    // Foydalanuvchilar
    // ------------------------------------------------------------------

    @GetMapping("/users")
    public AdminDto.Page<AdminDto.UserRow> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return userService.list(new AdminUserService.Filter(search, role, status, page, size));
    }

    @GetMapping("/users/{userId}")
    public AdminDto.UserDetail user(@PathVariable UUID userId) {
        return userService.detail(userId);
    }

    @PostMapping("/users/{userId}/block")
    public AdminDto.UserDetail block(@PathVariable UUID userId,
                                     @Valid @RequestBody AdminRequests.BlockRequest request) {
        return userService.block(userId, request.reason(), CurrentAdmin.requireId());
    }

    @PostMapping("/users/{userId}/unblock")
    public AdminDto.UserDetail unblock(@PathVariable UUID userId) {
        return userService.unblock(userId, CurrentAdmin.requireId());
    }

    @PostMapping("/users/{userId}/verify")
    public AdminDto.UserDetail verify(@PathVariable UUID userId,
                                      @Valid @RequestBody AdminRequests.VerifyRequest request) {
        return userService.verify(userId, request.level(), CurrentAdmin.requireId());
    }

    // ------------------------------------------------------------------
    // Shikoyatlar
    // ------------------------------------------------------------------

    @GetMapping("/reports")
    public AdminDto.Page<AdminDto.ReportRow> reports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return reportService.list(new AdminReportService.Filter(status, reason, page, size));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public AdminDto.ReportRow resolveReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody AdminRequests.ResolveReportRequest request) {
        return reportService.resolve(reportId, request.resolution(), request.note(),
                CurrentAdmin.requireId());
    }
}
