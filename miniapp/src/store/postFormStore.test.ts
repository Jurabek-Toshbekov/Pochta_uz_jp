import { beforeEach, describe, expect, it } from 'vitest';
import {
  MAX_CATEGORIES,
  allChecksAccepted,
  isStepComplete,
  toCreateRequest,
  toDraftPayload,
  usePostFormStore,
  weightError,
} from './postFormStore';

/** Formadan serverga o'tishdagi eng xatoga moyil joy — tipga o'girish (§1.5). */
describe('postFormStore', () => {
  beforeEach(() => {
    usePostFormStore.getState().reset();
  });

  function fillValidCarry() {
    usePostFormStore.getState().patch({
      postType: 'CARRY',
      direction: 'JP_UZ',
      originAirport: 'NRT',
      destAirport: 'TAS',
      date: '2026-12-01',
      dateFlexible: true,
      dateFlexibleDays: 3,
      weightKg: '5',
      weightKgMax: '20',
      priceAmount: '2000',
      priceCurrency: 'JPY',
      priceUnit: 'PER_KG',
      categoryIds: [1, 2],
      comment: '  izoh  ',
      contactTelegram: '@testuser',
    });
  }

  it('CARRY sanani departDate ga yozadi, deadlineDate bo‘sh qoladi', () => {
    fillValidCarry();
    const request = toCreateRequest(usePostFormStore.getState());

    expect(request.departDate).toBe('2026-12-01');
    expect(request.deadlineDate).toBeNull();
    expect(request.dateFlexibleDays).toBe(3);
  });

  it('SEND sanani deadlineDate ga yozadi', () => {
    fillValidCarry();
    usePostFormStore.getState().patch({ postType: 'SEND' });
    const request = toCreateRequest(usePostFormStore.getState());

    expect(request.deadlineDate).toBe('2026-12-01');
    expect(request.departDate).toBeNull();
  });

  it('raqamlar string emas, number bo‘lib ketadi', () => {
    fillValidCarry();
    const request = toCreateRequest(usePostFormStore.getState());

    expect(request.weightKg).toBe(5);
    expect(request.weightKgMax).toBe(20);
    expect(request.priceAmount).toBe(2000);
  });

  it('"Kelishamiz" tanlansa summa va valyuta yuborilmaydi (§6.3)', () => {
    fillValidCarry();
    usePostFormStore.getState().patch({ priceUnit: 'NEGOTIABLE' });
    const request = toCreateRequest(usePostFormStore.getState());

    expect(request.priceAmount).toBeNull();
    expect(request.priceCurrency).toBeNull();
    expect(request.priceUnit).toBe('NEGOTIABLE');
  });

  it('izoh trim qilinadi, username @ belgisiz ketadi', () => {
    fillValidCarry();
    const request = toCreateRequest(usePostFormStore.getState());

    expect(request.comment).toBe('izoh');
    expect(request.contactTelegram).toBe('testuser');
  });

  it('moslashuv o‘chirilgan bo‘lsa 0 yuboriladi', () => {
    fillValidCarry();
    usePostFormStore.getState().patch({ dateFlexible: false });

    expect(toCreateRequest(usePostFormStore.getState()).dateFlexibleDays).toBe(0);
  });

  it('checklist belgilanmasa safetyChecklistOk=false (§7.3)', () => {
    fillValidCarry();
    expect(toCreateRequest(usePostFormStore.getState()).safetyChecklistOk).toBe(false);

    usePostFormStore.getState().setCheck(0, true);
    usePostFormStore.getState().setCheck(1, true);
    expect(allChecksAccepted(usePostFormStore.getState())).toBe(false);

    usePostFormStore.getState().setCheck(2, true);
    expect(toCreateRequest(usePostFormStore.getState()).safetyChecklistOk).toBe(true);
  });

  it('erkin shahar yozilsa aeroport kodi yuborilmaydi va teskarisi', () => {
    fillValidCarry();
    usePostFormStore.getState().patch({ originAirport: null, originCityFree: 'Yokohama' });
    const request = toCreateRequest(usePostFormStore.getState());

    expect(request.originAirport).toBeNull();
    expect(request.originCityFree).toBe('Yokohama');
    expect(request.destCityFree).toBeNull();
  });

  describe('isStepComplete', () => {
    it('1-qadam: tur va yo‘nalish kerak', () => {
      expect(isStepComplete('step1_type', usePostFormStore.getState())).toBe(false);
      usePostFormStore.getState().patch({ postType: 'CARRY' });
      expect(isStepComplete('step1_type', usePostFormStore.getState())).toBe(false);
      usePostFormStore.getState().patch({ direction: 'JP_UZ' });
      expect(isStepComplete('step1_type', usePostFormStore.getState())).toBe(true);
    });

    it('2-qadam: sana to‘liq bo‘lishi kerak', () => {
      fillValidCarry();
      expect(isStepComplete('step2_route', usePostFormStore.getState())).toBe(true);
      usePostFormStore.getState().patch({ date: '2026-12' });
      expect(isStepComplete('step2_route', usePostFormStore.getState())).toBe(false);
    });

    it('3-qadam: narx yoki "kelishamiz" kerak', () => {
      fillValidCarry();
      usePostFormStore.getState().patch({ priceAmount: '' });
      expect(isStepComplete('step3_cargo', usePostFormStore.getState())).toBe(false);
      usePostFormStore.getState().patch({ priceUnit: 'NEGOTIABLE' });
      expect(isStepComplete('step3_cargo', usePostFormStore.getState())).toBe(true);
    });

    /**
     * Telegram ham, telefon ham majburiy: bittasi bo'lsa "Bog'lanish"
     * bosgan odam ko'pincha javob ololmaydi va bitim boshlanmaydi.
     */
    it('4-qadam: Telegram ham, telefon ham kerak', () => {
      expect(isStepComplete('step4_contact', usePostFormStore.getState())).toBe(false);

      usePostFormStore.getState().patch({ contactPhone: '+998901234567' });
      expect(isStepComplete('step4_contact', usePostFormStore.getState())).toBe(false);

      usePostFormStore.getState().patch({ contactTelegram: 'testuser' });
      expect(isStepComplete('step4_contact', usePostFormStore.getState())).toBe(true);
    });

    /**
     * Chegaradan chiqqan qiymat "Kanalga yuborish"gacha yetib bormasligi
     * kerak: server 400 qaytaradi va butun forma qaytadan to'ldiriladi.
     */
    it('3-qadam: 100 kg dan og‘ir yuk o‘tkazilmaydi', () => {
      fillValidCarry();
      usePostFormStore.getState().patch({ weightKgMax: '111' });

      expect(weightError(usePostFormStore.getState())).toBe('range');
      expect(isStepComplete('step3_cargo', usePostFormStore.getState())).toBe(false);
    });

    it('3-qadam: maksimal og‘irlik minimaldan kichik bo‘lmaydi', () => {
      fillValidCarry();
      usePostFormStore.getState().patch({ weightKg: '20', weightKgMax: '5' });

      expect(weightError(usePostFormStore.getState())).toBe('order');
      expect(isStepComplete('step3_cargo', usePostFormStore.getState())).toBe(false);
    });
  });

  it('kategoriya soni chegaradan oshmaydi (server @Size(max=5) bilan bir xil)', () => {
    const { toggleCategory } = usePostFormStore.getState();
    [1, 2, 3, 4, 5, 6, 7].forEach((id) => toggleCategory(id));

    expect(usePostFormStore.getState().categoryIds).toHaveLength(MAX_CATEGORIES);
    expect(usePostFormStore.getState().categoryIds).not.toContain(6);

    // Tanlanganini olib tashlash baribir ishlaydi.
    usePostFormStore.getState().toggleCategory(1);
    expect(usePostFormStore.getState().categoryIds).not.toContain(1);
  });

  it('draft payloadida checklist saqlanmaydi (§7.3)', () => {
    fillValidCarry();
    usePostFormStore.getState().setCheck(0, true);
    const payload = toDraftPayload(usePostFormStore.getState());

    expect(payload).not.toHaveProperty('checks');
    expect(payload).not.toHaveProperty('formStartedAtMs');
    expect(payload.originAirport).toBe('NRT');
  });

  it('eski draftdagi ortiqcha kategoriya tiklanishda kesiladi', () => {
    // Chegara qo'shilishidan oldin saqlangan draft.
    usePostFormStore.getState().hydrate({ categoryIds: [1, 2, 3, 4, 5, 6, 7] });

    expect(usePostFormStore.getState().categoryIds).toHaveLength(MAX_CATEGORIES);
  });

  it('draftdan tiklanganda checklist qaytadan belgilanadi', () => {
    usePostFormStore.getState().hydrate({ postType: 'CARRY', checks: [true, true, true] });
    expect(allChecksAccepted(usePostFormStore.getState())).toBe(false);
  });
});
