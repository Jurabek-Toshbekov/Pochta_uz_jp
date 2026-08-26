/**
 * Klientdan yuboriladigan event nomlari.
 * Backend `EventName.CLIENT_ALLOWED` bilan bir xil bo'lishi shart —
 * ro'yxatda yo'q nom jimgina tashlab yuboriladi (docs/EVENTS.md).
 */
export const EV = {
  APP_OPEN: 'app_open',
  APP_CLOSE: 'app_close',
  DEEP_LINK_OPEN: 'deep_link_open',
  LANGUAGE_CHANGED: 'language_changed',

  POST_FORM_OPEN: 'post_form_open',
  POST_FORM_STEP_VIEW: 'post_form_step_view',
  POST_FORM_STEP_COMPLETE: 'post_form_step_complete',
  POST_FORM_STEP_BACK: 'post_form_step_back',
  POST_FORM_FIELD_ERROR: 'post_form_field_error',
  POST_FORM_ABANDON: 'post_form_abandon',
  POST_DRAFT_SAVED: 'post_draft_saved',
  POST_PREVIEW_VIEW: 'post_preview_view',
  SAFETY_CHECKLIST_VIEW: 'safety_checklist_view',
  SAFETY_CHECKLIST_ACCEPT: 'safety_checklist_accept',

  SEARCH_OPEN: 'search_open',
  SEARCH_FILTER_CHANGE: 'search_filter_change',
  SEARCH_RESULT_CLICK: 'search_result_click',

  POST_VIEW: 'post_view',
  POST_DETAIL_VIEW: 'post_detail_view',
  CONTACT_CLICK: 'contact_click',
  POST_SHARE: 'post_share',

  NOTIFICATION_OPENED: 'notification_opened',
} as const;

export type EventNameValue = (typeof EV)[keyof typeof EV];

/** Forma qadamlari — voronka o'lchovi shu kodlarga tayanadi (§6.3). */
export const STEP = {
  TYPE: 'step1_type',
  ROUTE: 'step2_route',
  CARGO: 'step3_cargo',
  CONTACT: 'step4_contact',
  PREVIEW: 'preview',
} as const;

export type StepKey = (typeof STEP)[keyof typeof STEP];

export const STEP_ORDER: StepKey[] = [STEP.TYPE, STEP.ROUTE, STEP.CARGO, STEP.CONTACT];
