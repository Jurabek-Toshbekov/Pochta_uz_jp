import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiRequestError } from '../api/client';
import { api } from '../api/endpoints';
import type { SettingRow, SettingValue } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Card, EmptyState, ErrorState, Loader } from '../components/ui';
import { formatDateTime } from '../lib/format';

/**
 * Sozlamalar va feature flag'lar (§11.2 — /settings).
 *
 * <p>O'zgartirish faqat {@code ADMIN} uchun: feature flag butun mahsulot
 * xatti-harakatini o'zgartiradi. Moderator ko'radi, lekin tegolmaydi.
 *
 * <p>Har bir o'zgarish audit jurnaliga tushadi.
 */
export function SettingsPage() {
  const { role } = useAuth();
  const client = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.settings() });

  const update = useMutation({
    mutationFn: (input: { key: string; value: SettingValue }) =>
      api.updateSetting(input.key, input.value),
    onSuccess: () => {
      setError(null);
      void client.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: (cause) =>
      setError(cause instanceof ApiRequestError ? cause.message : 'Saqlanmadi.'),
  });

  const editable = role === 'ADMIN';

  if (settings.isLoading) {
    return <Loader />;
  }
  if (settings.isError || !settings.data) {
    return <ErrorState message="Sozlamalar yuklanmadi." onRetry={() => void settings.refetch()} />;
  }

  return (
    <>
      <div className="page-head">
        <h1>Sozlamalar</h1>
        <span className="muted">
          {editable ? 'O‘zgarish darhol kuchga kiradi' : 'Faqat ko‘rish — o‘zgartirish ADMIN huquqi'}
        </span>
      </div>

      {error && <p className="error">{error}</p>}

      <Card note="Har bir o‘zgarish audit jurnaliga yoziladi: kim, qachon va qaysi qiymatdan qaysi qiymatga o‘tkazgani ko‘rinadi.">
        {settings.data.length === 0 ? (
          <EmptyState text="Sozlama yo‘q." />
        ) : (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Sozlama</th>
                  <th>Kalit</th>
                  <th style={{ minWidth: 200 }}>Qiymat</th>
                  <th>Oxirgi o‘zgarish</th>
                </tr>
              </thead>
              <tbody>
                {settings.data.map((setting) => (
                  <SettingLine
                    key={setting.key}
                    setting={setting}
                    editable={editable}
                    busy={update.isPending}
                    onSave={(value) => update.mutate({ key: setting.key, value })}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  );
}

function SettingLine({
  setting,
  editable,
  busy,
  onSave,
}: {
  setting: SettingRow;
  editable: boolean;
  busy: boolean;
  onSave: (value: SettingValue) => void;
}) {
  const [draft, setDraft] = useState(String(setting.value));

  return (
    <tr>
      <td style={{ whiteSpace: 'normal' }}>
        {setting.titleUz}
        {setting.descriptionUz && (
          <div className="muted" style={{ fontSize: 12 }}>
            {setting.descriptionUz}
          </div>
        )}
      </td>
      <td className="mono muted">{setting.key}</td>
      <td>
        {setting.valueType === 'BOOLEAN' ? (
          <label style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <input
              type="checkbox"
              checked={setting.value === true}
              disabled={!editable || busy}
              aria-label={setting.titleUz}
              onChange={(event) => onSave(event.target.checked)}
              style={{ width: 'auto' }}
            />
            {setting.value === true ? 'Yoqilgan' : 'O‘chirilgan'}
          </label>
        ) : (
          <span style={{ display: 'inline-flex', gap: 6 }}>
            <input
              className="mono"
              value={draft}
              disabled={!editable || busy}
              aria-label={setting.titleUz}
              onChange={(event) => setDraft(event.target.value)}
              style={{ width: 100 }}
            />
            {editable && (
              <button
                type="button"
                disabled={busy || draft === String(setting.value)}
                onClick={() =>
                  onSave(
                    setting.valueType === 'NUMBER' ? Number.parseInt(draft, 10) || 0 : draft,
                  )
                }
              >
                Saqlash
              </button>
            )}
          </span>
        )}
      </td>
      <td className="mono muted">{formatDateTime(setting.updatedAt)}</td>
    </tr>
  );
}
