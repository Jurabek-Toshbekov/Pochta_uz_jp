import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, request } from '../api/client';
import type { LanguageCode } from '../api/types';
import {
  ErrorState,
  Field,
  GhostButton,
  Loader,
  Notice,
  PrimaryButton,
  uiStyles as styles,
} from '../components/primitives';
import { useProfile, useUpdateProfile } from '../hooks/useProfile';
import { useBackButton } from '../hooks/useTelegram';
import { LANGUAGES } from '../i18n';
import { useT } from '../i18n/useT';
import { useLanguage } from '../store/appStore';

/**
 * `/profile` — til, reyting, ma'lumotlarim (§9.1).
 *
 * Til shu yerdan boshqariladi va serverda saqlanadi: bot ham, xabarnomalar
 * ham `users.ui_language` dan o'qiydi, ya'ni faqat brauzerda saqlangan
 * tanlov yarim ishlagan bo'lardi.
 */
export function ProfilePage() {
  const t = useT();
  const language = useLanguage();
  const navigate = useNavigate();

  const profile = useProfile();
  const update = useUpdateProfile();

  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [phoneError, setPhoneError] = useState<string | undefined>(undefined);
  const [exporting, setExporting] = useState(false);

  useBackButton(useCallback(() => navigate('/'), [navigate]));

  useEffect(() => {
    if (profile.data) {
      setPhone(profile.data.phone ?? '');
    }
  }, [profile.data]);

  async function save(body: { uiLanguage?: LanguageCode; phone?: string }) {
    setMessage(null);
    setError(null);
    setPhoneError(undefined);
    try {
      await update.mutateAsync(body);
      setMessage(t.profile.saved);
    } catch (cause) {
      if (cause instanceof ApiError) {
        setPhoneError(cause.fieldErrors.phone);
        setError(cause.fieldErrors.phone ? null : cause.message);
      } else {
        setError(t.common.errorNetwork);
      }
    }
  }

  /**
   * Eksport (§7.2). So'rov `initData` header'i bilan ketishi kerak, shuning
   * uchun oddiy havola emas — javob olinib, brauzerga fayl sifatida
   * beriladi. Telegram webview yuklab olishni bermasa, botdagi
   * `/mening_malumotlarim` o'sha faylni yuboradi.
   */
  async function exportData() {
    setExporting(true);
    setError(null);
    try {
      const data = await request<unknown>('/api/miniapp/me/export');
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'pochta-malumotlarim.json';
      link.click();
      URL.revokeObjectURL(url);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : t.common.errorNetwork);
    } finally {
      setExporting(false);
    }
  }

  if (profile.isLoading) {
    return (
      <div className="page">
        <Loader text={t.common.loading} />
      </div>
    );
  }

  if (profile.isError || !profile.data) {
    return (
      <div className="page">
        <ErrorState
          message={t.common.errorNetwork}
          retryLabel={t.common.retry}
          onRetry={() => void profile.refetch()}
        />
      </div>
    );
  }

  const me = profile.data;
  const displayName = [me.firstName, me.lastName].filter(Boolean).join(' ');
  const memberSince = new Date(me.firstSeenAt).toISOString().slice(0, 10);

  return (
    <div className="page">
      <h1>{t.profile.title}</h1>
      <p className="muted">
        {displayName}
        {me.username ? ` · @${me.username}` : ''}
      </p>

      <div className={styles.card}>
        <span className={styles.label}>{t.profile.stats}</span>
        <p>
          {me.postCount} {t.profile.posts} · {me.activePostCount} {t.profile.activePosts} ·{' '}
          {me.dealCount} {t.profile.deals}
        </p>
        <p>
          {t.profile.rating}:{' '}
          {me.averageRating === null
            ? t.profile.noRating
            : `${me.averageRating.toFixed(1)} (${me.reviewCount} ${t.profile.reviews})`}
        </p>
        <p className={styles.hint}>
          {t.profile.trust}: {me.trustScore} · {t.profile.verification}:{' '}
          {t.verification[me.verificationLevel]}
        </p>
        <p className={styles.hint}>
          {t.profile.memberSince}: {memberSince}
        </p>
      </div>

      <div className={styles.card}>
        <span className={styles.label}>{t.profile.language}</span>
        <div className={styles.row}>
          {LANGUAGES.map((code) => (
            <button
              key={code}
              type="button"
              aria-pressed={language === code}
              className={`${styles.chip} ${language === code ? styles.chipActive : ''}`}
              onClick={() => {
                if (code !== language) {
                  void save({ uiLanguage: code });
                }
              }}
            >
              {t.language[code]}
            </button>
          ))}
        </div>
      </div>

      <Field label={t.profile.phone} hint={t.profile.phoneHint} error={phoneError}>
        <input
          type="tel"
          inputMode="tel"
          value={phone}
          maxLength={32}
          aria-label={t.profile.phone}
          placeholder={t.step4.phonePlaceholder}
          className={phoneError ? styles.inputError : undefined}
          onChange={(event) => setPhone(event.target.value)}
        />
      </Field>
      <PrimaryButton
        label={update.isPending ? t.common.loading : t.profile.phoneSave}
        disabled={update.isPending || phone === (me.phone ?? '')}
        onClick={() => void save({ phone })}
      />
      {me.phone ? (
        <GhostButton
          label={t.profile.phoneRemove}
          onClick={() => {
            setPhone('');
            void save({ phone: '' });
          }}
        />
      ) : null}

      <div className={styles.card}>
        <span className={styles.label}>{t.profile.dataTitle}</span>
        <p className={styles.hint}>{t.profile.dataHint}</p>
        <GhostButton
          label={exporting ? t.common.loading : t.profile.exportData}
          onClick={() => void exportData()}
        />
      </div>

      {message ? <Notice>{message}</Notice> : null}
      {error ? <Notice>{error}</Notice> : null}
    </div>
  );
}
