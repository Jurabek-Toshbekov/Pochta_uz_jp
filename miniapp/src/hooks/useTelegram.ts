import { useEffect } from 'react';
import WebApp from '@twa-dev/sdk';
import { initSafeArea, type SafeAreaCapableWebApp } from '../lib/safeArea';

/**
 * Telegram SDK bilan ishlash (§9.3).
 *
 * Qoidalar:
 *  - asosiy harakat uchun `MainButton` ishlatiladi, o'z tugmasi yasalmaydi
 *  - qadamlar orasida `BackButton`
 *  - fon va matn ranglari `themeParams`dan olinadi
 *  - xavfsiz zona hisobga olinadi, vertikal svip o'chiriladi (§9.6)
 */

/** Ilova ochilishida bir marta chaqiriladi. */
export function initTelegram(): void {
  try {
    WebApp.ready();
    WebApp.expand();
    applyTheme();
    WebApp.onEvent('themeChanged', applyTheme);
    // §9.6 — iPhone kamera kesigi ostida matn qolmasligi uchun.
    // `@twa-dev/sdk@8` turlarida bu metodlar hali yo'q, shuning uchun tor
    // interfeysga o'tkazamiz (`any` taqiqlangan — §14).
    initSafeArea(WebApp as unknown as SafeAreaCapableWebApp, document.documentElement);
  } catch {
    // Brauzerda (Telegram tashqarisida) ochilgan bo'lsa jim o'tamiz —
    // ishlab chiqish paytida qulay.
  }
}

function applyTheme(): void {
  const scheme = WebApp.colorScheme === 'dark' ? 'dark' : 'light';
  document.documentElement.setAttribute('data-tg-theme', scheme);

  const background = WebApp.themeParams?.secondary_bg_color || WebApp.themeParams?.bg_color;
  if (background) {
    document.documentElement.style.setProperty('--surface-sunken', background);
  }
  const surface = WebApp.themeParams?.bg_color;
  if (surface && WebApp.themeParams?.secondary_bg_color) {
    document.documentElement.style.setProperty('--surface', surface);
  }
}

export function haptic(style: 'light' | 'medium' | 'heavy' = 'light'): void {
  try {
    WebApp.HapticFeedback.impactOccurred(style);
  } catch {
    // Haptic ba'zi platformalarda yo'q.
  }
}

export function hapticSuccess(): void {
  try {
    WebApp.HapticFeedback.notificationOccurred('success');
  } catch {
    // e'tiborsiz
  }
}

interface MainButtonOptions {
  text: string;
  onClick: () => void;
  enabled?: boolean;
  visible?: boolean;
  loading?: boolean;
}

/** `MainButton` — ekranning asosiy harakati. */
export function useMainButton({
  text,
  onClick,
  enabled = true,
  visible = true,
  loading = false,
}: MainButtonOptions): void {
  useEffect(() => {
    const button = WebApp.MainButton;
    if (!button) {
      return;
    }
    button.setText(text);
    if (enabled) {
      button.enable();
    } else {
      button.disable();
    }
    if (loading) {
      button.showProgress(false);
    } else {
      button.hideProgress();
    }
    if (visible) {
      button.show();
    } else {
      button.hide();
    }
    button.onClick(onClick);
    return () => {
      button.offClick(onClick);
      button.hideProgress();
      button.hide();
    };
  }, [text, onClick, enabled, visible, loading]);
}

/** `BackButton` — qadamlar orasida. */
export function useBackButton(onBack: (() => void) | null): void {
  useEffect(() => {
    const button = WebApp.BackButton;
    if (!button) {
      return;
    }
    if (!onBack) {
      button.hide();
      return;
    }
    button.show();
    button.onClick(onBack);
    return () => {
      button.offClick(onBack);
      button.hide();
    };
  }, [onBack]);
}

/** Yopish/bekor qilishda tasdiq so'raladi (§9.3). */
export function confirmLeave(message: string, onConfirm: () => void): void {
  try {
    WebApp.showConfirm(message, (confirmed) => {
      if (confirmed) {
        onConfirm();
      }
    });
  } catch {
    onConfirm();
  }
}

export function shareUrl(url: string, text: string): void {
  const target = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`;
  try {
    WebApp.openTelegramLink(target);
  } catch {
    window.open(target, '_blank');
  }
}

export function openLink(url: string): void {
  try {
    if (url.startsWith('https://t.me/')) {
      WebApp.openTelegramLink(url);
    } else {
      WebApp.openLink(url);
    }
  } catch {
    window.open(url, '_blank');
  }
}

export function telegramUser() {
  try {
    return WebApp.initDataUnsafe?.user ?? null;
  } catch {
    return null;
  }
}

export function startParam(): string | null {
  try {
    return WebApp.initDataUnsafe?.start_param ?? null;
  } catch {
    return null;
  }
}
