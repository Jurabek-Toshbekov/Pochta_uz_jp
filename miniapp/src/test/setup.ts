import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

/**
 * Telegram SDK testlarda mavjud emas — minimal stub qo'yamiz.
 * MainButton/BackButton chaqiruvlari testda tekshiriladi.
 */
const noop = () => undefined;

const mainButton = {
  setText: vi.fn(),
  enable: vi.fn(),
  disable: vi.fn(),
  show: vi.fn(),
  hide: vi.fn(),
  showProgress: vi.fn(),
  hideProgress: vi.fn(),
  onClick: vi.fn(),
  offClick: vi.fn(),
};

const backButton = {
  show: vi.fn(),
  hide: vi.fn(),
  onClick: vi.fn(),
  offClick: vi.fn(),
};

vi.mock('@twa-dev/sdk', () => ({
  default: {
    initData: 'tma-test-init-data',
    initDataUnsafe: { user: { id: 1, first_name: 'Test', username: 'testuser' }, start_param: null },
    platform: 'web',
    colorScheme: 'light',
    themeParams: {},
    MainButton: mainButton,
    BackButton: backButton,
    HapticFeedback: {
      impactOccurred: noop,
      notificationOccurred: noop,
    },
    ready: noop,
    expand: noop,
    onEvent: noop,
    showConfirm: noop,
    openLink: noop,
    openTelegramLink: noop,
    requestContact: noop,
  },
}));

export const telegramStub = { mainButton, backButton };

if (!globalThis.crypto?.randomUUID) {
  Object.defineProperty(globalThis, 'crypto', {
    value: { randomUUID: () => '00000000-0000-4000-8000-000000000000' },
  });
}
