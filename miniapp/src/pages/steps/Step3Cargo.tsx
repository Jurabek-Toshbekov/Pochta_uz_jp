import type { CargoCategory } from '../../api/types';
import { usePostFormStore } from '../../store/postFormStore';
import { useT } from '../../i18n/useT';
import {
  Chip,
  ChipGroup,
  Field,
  Notice,
  SegmentedControl,
  uiStyles as styles,
} from '../../components/primitives';
import { highRiskWarnings } from '../../hooks/useReference';
import { STEP } from '../../analytics/events';

const COMMENT_MAX = 1000;

interface Props {
  categories: CargoCategory[];
  fieldErrors: Record<string, string>;
}

/**
 * 3-qadam — yuk va narx (§9.2).
 *
 * HIGH risk kategoriya tanlansa ogohlantirish DARHOL ko'rinadi (§7.3) —
 * publish oldidan emas, hozir: odam hali qaroridan qaytishi mumkin.
 */
export function Step3Cargo({ categories, fieldErrors }: Props) {
  const t = useT();
  const state = usePostFormStore();
  const { patch, toggleCategory } = state;

  const warnings = highRiskWarnings(categories, state.categoryIds);
  const negotiable = state.priceUnit === 'NEGOTIABLE';

  return (
    <div className={`${styles.section} ${styles.slide}`}>
      <h2>{t.step3.title}</h2>

      <Field label={t.step3.categories} hint={t.step3.categoriesHint} error={fieldErrors.categoryIds}>
        <ChipGroup label={t.step3.categories}>
          {categories.map((category) => (
            <Chip
              key={category.id}
              label={`${category.emoji ?? ''} ${category.titleUz}`.trim()}
              active={state.categoryIds.includes(category.id)}
              onToggle={() => toggleCategory(category.id)}
            />
          ))}
        </ChipGroup>
      </Field>

      {warnings.map((category) => (
        <Notice key={category.id} title={`${t.step3.riskWarning}: ${category.titleUz}`}>
          {category.warningUz}
        </Notice>
      ))}

      <Field label={`${t.step3.weight} (${t.common.kg})`} error={fieldErrors.weightKg ?? fieldErrors.weightKgMax}>
        <div className={`${styles.row} ${styles.rowFill}`}>
          <input
            type="number"
            inputMode="decimal"
            min={0.1}
            max={100}
            step={0.5}
            value={state.weightKg}
            aria-label={`${t.step3.weight} ${t.step3.weightFrom}`}
            placeholder={t.step3.weightFrom}
            onChange={(event) => patch({ weightKg: event.target.value }, STEP.CARGO)}
          />
          <input
            type="number"
            inputMode="decimal"
            min={0.1}
            max={100}
            step={0.5}
            value={state.weightKgMax}
            aria-label={`${t.step3.weight} ${t.step3.weightTo}`}
            placeholder={t.step3.weightTo}
            onChange={(event) => patch({ weightKgMax: event.target.value }, STEP.CARGO)}
          />
        </div>
      </Field>

      <Field label={t.step3.unit}>
        <SegmentedControl
          ariaLabel={t.step3.unit}
          value={state.priceUnit}
          onChange={(value) => patch({ priceUnit: value }, STEP.CARGO)}
          options={[
            { value: 'PER_KG', label: t.step3.unitPerKg },
            { value: 'TOTAL', label: t.step3.unitTotal },
            { value: 'NEGOTIABLE', label: t.step3.unitNegotiable },
          ]}
        />
      </Field>

      {negotiable ? null : (
        <Field label={t.step3.price} error={fieldErrors.priceAmount ?? fieldErrors.priceCurrency}>
          <div className={`${styles.row} ${styles.rowFill}`}>
            <input
              type="number"
              inputMode="decimal"
              min={1}
              value={state.priceAmount}
              aria-label={t.step3.price}
              className={fieldErrors.priceAmount ? styles.inputError : undefined}
              onChange={(event) => patch({ priceAmount: event.target.value }, STEP.CARGO)}
            />
            <SegmentedControl
              ariaLabel={t.step3.currency}
              value={state.priceCurrency}
              onChange={(value) => patch({ priceCurrency: value }, STEP.CARGO)}
              options={[
                { value: 'JPY', label: '¥' },
                { value: 'USD', label: '$' },
                { value: 'UZS', label: "so'm" },
              ]}
            />
          </div>
        </Field>
      )}

      <Field
        label={t.step3.comment}
        hint={`${state.comment.length}/${COMMENT_MAX}`}
        error={fieldErrors.comment}
      >
        <textarea
          rows={4}
          maxLength={COMMENT_MAX}
          value={state.comment}
          aria-label={t.step3.comment}
          placeholder={t.step3.commentPlaceholder}
          onChange={(event) => patch({ comment: event.target.value }, STEP.CARGO)}
        />
      </Field>
    </div>
  );
}
