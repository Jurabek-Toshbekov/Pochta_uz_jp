/**
 * Xavfsiz zona (safe area) — CLAUDE.md §9.6.
 *
 * Muammo: Telegram Mini App iPhone'da to'liq ekranga cho'zilganda sahifaning
 * eng tepasidagi matn "dynamic island" / kamera kesigi ostiga tushib ko'rinmay
 * qoladi. Foydalanuvchi sahifani yuqoriga ortiqcha sursa, Telegram buni
 * "yopish" imosi deb tushunib webview'ni siljitadi va matn yana yo'qoladi.
 *
 * Yechim uch qatlamli:
 *  1. `env(safe-area-inset-*)` — WKWebView o'zi beradigan qiymat
 *     (`viewport-fit=cover` bo'lgani uchun ishlaydi, `index.html`ga qara).
 *  2. Telegram 8.0+ dagi `safeAreaInset` (qurilma kesigi) va
 *     `contentSafeAreaInset` (Telegram o'z boshqaruv elementlari egallagan
 *     joy). Rasmiy hujjatga ko'ra ular QO'SHILADI, biri ikkinchisini
 *     almashtirmaydi.
 *  3. `disableVerticalSwipes()` — ortiqcha surishda webview siljishini
 *     to'xtatadi.
 *
 * CSS tomonda yakuniy qiymat `max(env(...), tg-safe + tg-content)` sifatida
 * hisoblanadi (`tokens.css`) — qaysi manba kattaroq bo'lsa, o'sha ishlatiladi,
 * shuning uchun ikki marta qo'shilib ketmaydi.
 */

/** Telegram qaytaradigan inset (piksel). */
export interface SafeAreaInset {
  top: number;
  bottom: number;
  left: number;
  right: number;
}

/** CSS o'zgaruvchilari nomlari — `tokens.css` bilan bir xil bo'lishi shart. */
export const SAFE_AREA_VARS = {
  top: '--tg-safe-top',
  bottom: '--tg-safe-bottom',
  left: '--tg-safe-left',
  right: '--tg-safe-right',
} as const;

/**
 * Ikki insetdan CSS o'zgaruvchilari to'plamini yasaydi.
 *
 * Sof funksiya — test shu yerga yoziladi. Manfiy va `NaN` qiymatlar 0 ga
 * keltiriladi: eski mijozlar ba'zan to'liq bo'lmagan obyekt qaytaradi.
 */
export function safeAreaVars(
  device?: Partial<SafeAreaInset> | null,
  content?: Partial<SafeAreaInset> | null,
): Record<string, string> {
  const sum = (a?: number, b?: number): string => `${clamp(a) + clamp(b)}px`;
  return {
    [SAFE_AREA_VARS.top]: sum(device?.top, content?.top),
    [SAFE_AREA_VARS.bottom]: sum(device?.bottom, content?.bottom),
    [SAFE_AREA_VARS.left]: sum(device?.left, content?.left),
    [SAFE_AREA_VARS.right]: sum(device?.right, content?.right),
  };
}

function clamp(value?: number): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.round(value) : 0;
}

/**
 * Telegram SDK'ning bizga kerak bo'lgan, lekin `@twa-dev/sdk@8` turlarida
 * hali e'lon qilinmagan qismi. `any` ishlatilmaydi (§14) — tor interfeys.
 */
export interface SafeAreaCapableWebApp {
  safeAreaInset?: Partial<SafeAreaInset>;
  contentSafeAreaInset?: Partial<SafeAreaInset>;
  disableVerticalSwipes?: () => void;
  onEvent?: (event: string, handler: () => void) => void;
}

/** Joriy qiymatlarni `<html>` elementiga yozadi. */
export function applySafeArea(webApp: SafeAreaCapableWebApp, root: HTMLElement): void {
  const vars = safeAreaVars(webApp.safeAreaInset, webApp.contentSafeAreaInset);
  for (const [name, value] of Object.entries(vars)) {
    root.style.setProperty(name, value);
  }
}

/**
 * Ilova ochilishida bir marta chaqiriladi: qiymatlarni yozadi, o'zgarishlarga
 * obuna bo'ladi va vertikal svipni o'chiradi.
 *
 * Eski mijozlarda (Bot API < 8.0) bu metodlar yo'q — o'shanda faqat
 * `env(safe-area-inset-*)` ishlaydi va bu yetarli.
 */
export function initSafeArea(webApp: SafeAreaCapableWebApp, root: HTMLElement): void {
  applySafeArea(webApp, root);

  webApp.disableVerticalSwipes?.();

  const refresh = (): void => applySafeArea(webApp, root);
  webApp.onEvent?.('safeAreaChanged', refresh);
  webApp.onEvent?.('contentSafeAreaChanged', refresh);
  webApp.onEvent?.('viewportChanged', refresh);
}
