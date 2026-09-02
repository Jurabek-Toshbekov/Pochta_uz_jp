import { useState } from 'react';
import { ApiRequestError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/**
 * Kirish (§11.1).
 *
 * <p>Parol yo'q: bot {@code /admin} buyrug'iga javoban bir martalik kod
 * beradi. Matn nima qilish kerakligini aytadi, kechirim so'ramaydi (§9.4).
 */
export function LoginPage() {
  const { login } = useAuth();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(code.trim());
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause.message
          : 'Serverga ulanib bo‘lmadi. Internetni tekshiring.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <form className="login__card" onSubmit={submit}>
        <h1>Boshqaruv paneli</h1>
        <p className="muted">
          Telegram botga <span className="mono">/admin</span> deb yozing — u sizga 5 daqiqa
          amal qiladigan kod yuboradi.
        </p>

        <input
          className="login__code"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          placeholder="XXXXXXXX"
          maxLength={16}
          autoFocus
          aria-label="Kirish kodi"
        />

        {error && <p className="error">{error}</p>}

        <button type="submit" className="primary" disabled={busy || code.trim().length < 4}>
          {busy ? 'Tekshirilmoqda…' : 'Kirish'}
        </button>
      </form>
    </div>
  );
}
