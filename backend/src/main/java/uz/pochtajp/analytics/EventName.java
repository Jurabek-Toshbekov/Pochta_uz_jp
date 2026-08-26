package uz.pochtajp.analytics;

import java.util.Set;

/**
 * Event taksonomiyasi (CLAUDE.md §6.1, {@code docs/EVENTS.md}).
 *
 * <p>Yozilmagan harakat — yo'qolgan pul (§1.6). Shuning uchun nomlar bir joyda
 * turadi: frontend ham, backend ham shu ro'yxatdan foydalanadi.
 */
public final class EventName {

    private EventName() {
    }

    // --- Sessiya va kirish ---
    public static final String APP_OPEN = "app_open";
    public static final String APP_CLOSE = "app_close";
    public static final String BOT_COMMAND = "bot_command";
    public static final String DEEP_LINK_OPEN = "deep_link_open";
    public static final String LANGUAGE_CHANGED = "language_changed";

    // --- E'lon berish voronkasi ---
    public static final String POST_FORM_OPEN = "post_form_open";
    public static final String POST_FORM_STEP_VIEW = "post_form_step_view";
    public static final String POST_FORM_STEP_COMPLETE = "post_form_step_complete";
    public static final String POST_FORM_STEP_BACK = "post_form_step_back";
    public static final String POST_FORM_FIELD_ERROR = "post_form_field_error";
    public static final String POST_FORM_ABANDON = "post_form_abandon";
    public static final String POST_DRAFT_SAVED = "post_draft_saved";
    public static final String POST_PREVIEW_VIEW = "post_preview_view";
    public static final String SAFETY_CHECKLIST_VIEW = "safety_checklist_view";
    public static final String SAFETY_CHECKLIST_ACCEPT = "safety_checklist_accept";
    public static final String POST_SUBMIT = "post_submit";
    public static final String POST_PUBLISH_SUCCESS = "post_publish_success";
    public static final String POST_PUBLISH_FAIL = "post_publish_fail";
    public static final String POST_EDIT = "post_edit";
    public static final String POST_CLOSE = "post_close";

    // --- Qidiruv ---
    public static final String SEARCH_OPEN = "search_open";
    public static final String SEARCH_PERFORMED = "search_performed";
    public static final String SEARCH_ZERO_RESULT = "search_zero_result";
    public static final String SEARCH_FILTER_CHANGE = "search_filter_change";
    public static final String SEARCH_RESULT_CLICK = "search_result_click";
    public static final String SEARCH_SAVED = "search_saved";

    // --- Qiymat momenti ---
    public static final String POST_VIEW = "post_view";
    public static final String POST_DETAIL_VIEW = "post_detail_view";
    public static final String CONTACT_REVEAL = "contact_reveal";
    public static final String CONTACT_CLICK = "contact_click";
    public static final String POST_SHARE = "post_share";
    public static final String DEAL_CONFIRMED = "deal_confirmed";
    public static final String REVIEW_LEFT = "review_left";

    // --- Xabarnoma ---
    public static final String NOTIFICATION_SENT = "notification_sent";
    public static final String NOTIFICATION_OPENED = "notification_opened";
    public static final String NOTIFICATION_CONVERTED = "notification_converted";

    // --- Xavfsizlik ---
    public static final String REPORT_SUBMITTED = "report_submitted";
    public static final String POST_REJECTED = "post_rejected";
    public static final String USER_BLOCKED = "user_blocked";
    public static final String RATE_LIMIT_HIT = "rate_limit_hit";

    /**
     * Mini App yuborishi mumkin bo'lgan nomlar. Ro'yxatdan tashqarisi qabul
     * qilinmaydi — aks holda analitika "shovqin"ga to'lib ketadi va metrika
     * ta'riflari buziladi (§6.3).
     */
    public static final Set<String> CLIENT_ALLOWED = Set.of(
            APP_OPEN, APP_CLOSE, DEEP_LINK_OPEN, LANGUAGE_CHANGED,
            POST_FORM_OPEN, POST_FORM_STEP_VIEW, POST_FORM_STEP_COMPLETE, POST_FORM_STEP_BACK,
            POST_FORM_FIELD_ERROR, POST_FORM_ABANDON, POST_DRAFT_SAVED, POST_PREVIEW_VIEW,
            SAFETY_CHECKLIST_VIEW, SAFETY_CHECKLIST_ACCEPT,
            SEARCH_OPEN, SEARCH_FILTER_CHANGE, SEARCH_RESULT_CLICK,
            POST_VIEW, POST_DETAIL_VIEW, CONTACT_CLICK, POST_SHARE,
            NOTIFICATION_OPENED
    );

    public static boolean isAllowedFromClient(String name) {
        return name != null && CLIENT_ALLOWED.contains(name);
    }
}
