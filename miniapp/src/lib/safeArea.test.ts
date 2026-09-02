import { describe, expect, it, vi } from 'vitest';
import {
  SAFE_AREA_VARS,
  applySafeArea,
  initSafeArea,
  safeAreaVars,
  type SafeAreaCapableWebApp,
} from './safeArea';

describe('safeAreaVars', () => {
  it("qurilma va Telegram insetlarini qo'shadi", () => {
    const vars = safeAreaVars(
      { top: 59, bottom: 34, left: 0, right: 0 },
      { top: 46, bottom: 0, left: 0, right: 0 },
    );
    expect(vars[SAFE_AREA_VARS.top]).toBe('105px');
    expect(vars[SAFE_AREA_VARS.bottom]).toBe('34px');
  });

  it("inset yo'q bo'lsa nol qaytaradi (eski mijozlar)", () => {
    expect(safeAreaVars(null, undefined)[SAFE_AREA_VARS.top]).toBe('0px');
  });

  it("manfiy va NaN qiymatlarni 0 ga keltiradi", () => {
    const vars = safeAreaVars({ top: -20, bottom: Number.NaN }, {});
    expect(vars[SAFE_AREA_VARS.top]).toBe('0px');
    expect(vars[SAFE_AREA_VARS.bottom]).toBe('0px');
  });

  it("to'liq bo'lmagan obyektni ham qabul qiladi", () => {
    expect(safeAreaVars({ top: 47 })[SAFE_AREA_VARS.top]).toBe('47px');
  });
});

describe('applySafeArea', () => {
  it('CSS o\'zgaruvchilarini <html> ga yozadi', () => {
    const root = document.createElement('html');
    applySafeArea({ safeAreaInset: { top: 47 }, contentSafeAreaInset: { top: 10 } }, root);
    expect(root.style.getPropertyValue(SAFE_AREA_VARS.top)).toBe('57px');
  });
});

describe('initSafeArea', () => {
  it('vertikal svipni o\'chiradi va o\'zgarishlarga obuna bo\'ladi', () => {
    const disableVerticalSwipes = vi.fn();
    const onEvent = vi.fn();
    const webApp: SafeAreaCapableWebApp = {
      safeAreaInset: { top: 47 },
      disableVerticalSwipes,
      onEvent,
    };
    const root = document.createElement('html');

    initSafeArea(webApp, root);

    expect(disableVerticalSwipes).toHaveBeenCalledTimes(1);
    expect(onEvent.mock.calls.map((call) => call[0])).toEqual([
      'safeAreaChanged',
      'contentSafeAreaChanged',
      'viewportChanged',
    ]);
  });

  it("eski mijozda (metodlar yo'q) xato bermaydi", () => {
    const root = document.createElement('html');
    expect(() => initSafeArea({}, root)).not.toThrow();
    expect(root.style.getPropertyValue(SAFE_AREA_VARS.top)).toBe('0px');
  });

  it("obuna bo'lgan hodisa qiymatni yangilaydi", () => {
    const handlers: Record<string, () => void> = {};
    const webApp: SafeAreaCapableWebApp = {
      safeAreaInset: { top: 0 },
      onEvent: (event, handler) => {
        handlers[event] = handler;
      },
    };
    const root = document.createElement('html');
    initSafeArea(webApp, root);
    expect(root.style.getPropertyValue(SAFE_AREA_VARS.top)).toBe('0px');

    webApp.safeAreaInset = { top: 59 };
    handlers.safeAreaChanged?.();
    expect(root.style.getPropertyValue(SAFE_AREA_VARS.top)).toBe('59px');
  });
});
