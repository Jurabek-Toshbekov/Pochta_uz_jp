import type {
  Airport,
  CargoCategory,
  Currency,
  Direction,
  LanguageCode,
  PostType,
  PriceUnit,
} from '../api/types';
import type { BoardingPassData } from '../components/BoardingPassCard';

/**
 * Boarding pass kartasi uchun ma'lumot yig'ish.
 *
 * Bitta joyda turadi, chunki karta to'rt ekranda ishlatiladi (qidiruv,
 * tafsilot, ko'rib chiqish, mening e'lonlarim) va har birida qayta yozilsa
 * biri boshqasidan orqada qolib ketadi.
 *
 * Shahar va kategoriya nomlari joriy tilga ergashadi — reference ma'lumotda
 * `cityUz`/`cityRu` va `titleUz`/`titleRu` bor. Kirill yozuvidagi o'zbekcha
 * uchun alohida ustun yo'q, u lotin variantiga qaytadi.
 */
export interface PostLike {
  postType: PostType;
  direction: Direction;
  originAirport: string | null;
  destAirport: string | null;
  originCityFree: string | null;
  destCityFree: string | null;
  finalDestination: string | null;
  departDate: string | null;
  deadlineDate: string | null;
  dateFlexibleDays: number;
  weightKg: number | null;
  weightKgMax: number | null;
  priceAmount: number | null;
  priceCurrency: Currency | null;
  priceUnit: PriceUnit;
  categoryIds: number[];
  comment: string | null;
}

function normalizeCity(value: string): string {
  return value.trim().toLowerCase();
}

/**
 * Yakuniy manzil kelish shahrining o'zi bo'lsa — ortiqcha.
 *
 * Aeroportning UCHALA nomi bilan solishtiriladi: foydalanuvchi "Buxoro" deb
 * yozadi, ekran esa rus tilida "Бухара" ko'rsatadi. Faqat ko'rinadigan nom
 * bilan solishtirsak, til almashganda takrorlanish qaytib keladi.
 */
export function isRedundantFinalDestination(
  finalDestination: string | null,
  destAirport: Airport | undefined,
  destCityFree: string | null,
): boolean {
  if (!finalDestination || !finalDestination.trim()) {
    return true;
  }
  const target = normalizeCity(finalDestination);
  const candidates = [
    destAirport?.cityUz,
    destAirport?.cityRu,
    destAirport?.cityEn,
    destCityFree,
  ];
  return candidates.some((name) => name && normalizeCity(name) === target);
}

export function airportCity(airport: Airport | undefined, language: LanguageCode): string | null {
  if (!airport) {
    return null;
  }
  return language === 'ru' ? airport.cityRu : airport.cityUz;
}

export function toBoardingPassData(
  post: PostLike,
  airports: Airport[] | undefined,
  categories: CargoCategory[] | undefined,
  language: LanguageCode,
): BoardingPassData {
  const airportList = airports ?? [];
  const origin = airportList.find((airport) => airport.code === post.originAirport);
  const dest = airportList.find((airport) => airport.code === post.destAirport);

  return {
    postType: post.postType,
    originCode: post.originAirport,
    destCode: post.destAirport,
    originCityFree: post.originCityFree,
    destCityFree: post.destCityFree,
    originCity: airportCity(origin, language),
    destCity: airportCity(dest, language),
    finalDestination: isRedundantFinalDestination(
      post.finalDestination,
      dest,
      post.destCityFree,
    )
      ? null
      : post.finalDestination,
    date: post.departDate ?? post.deadlineDate,
    flexibleDays: post.dateFlexibleDays,
    weightKg: post.weightKg,
    weightKgMax: post.weightKgMax,
    priceAmount: post.priceAmount,
    priceCurrency: post.priceCurrency,
    priceUnit: post.priceUnit,
    // To'liq kategoriya obyektlari uzatiladi — karta nomni o'zi tanlaydi.
    categories: (categories ?? []).filter((category) => post.categoryIds.includes(category.id)),
    comment: post.comment,
  };
}
