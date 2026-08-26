import { usePostFormStore } from '../../store/postFormStore';
import { useT } from '../../i18n/useT';
import { ChoiceCard, Field, SegmentedControl, uiStyles as styles } from '../../components/primitives';
import { STEP } from '../../analytics/events';

/** 1-qadam — Nima qilmoqchisiz (§9.2). Ikkita katta karta + yo'nalish. */
export function Step1Type() {
  const t = useT();
  const { postType, direction, patch } = usePostFormStore();

  return (
    <div className={`${styles.section} ${styles.slide}`}>
      <h2>{t.step1.title}</h2>

      <div className={styles.section} role="radiogroup" aria-label={t.step1.title}>
        <ChoiceCard
          title={`📦 ${t.step1.send}`}
          hint={t.step1.sendHint}
          active={postType === 'SEND'}
          onSelect={() => patch({ postType: 'SEND' }, STEP.TYPE)}
        />
        <ChoiceCard
          title={`✈️ ${t.step1.carry}`}
          hint={t.step1.carryHint}
          active={postType === 'CARRY'}
          onSelect={() => patch({ postType: 'CARRY' }, STEP.TYPE)}
        />
      </div>

      <Field label={t.step1.direction}>
        <SegmentedControl
          ariaLabel={t.step1.direction}
          value={direction}
          onChange={(value) => patch({ direction: value, originAirport: null, destAirport: null }, STEP.TYPE)}
          options={[
            { value: 'JP_UZ', label: '🇯🇵 → 🇺🇿' },
            { value: 'UZ_JP', label: '🇺🇿 → 🇯🇵' },
          ]}
        />
      </Field>

      <p className={styles.hint}>
        {direction === 'JP_UZ' ? t.step1.jpToUz : direction === 'UZ_JP' ? t.step1.uzToJp : ''}
      </p>
    </div>
  );
}
