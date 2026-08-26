import { useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { Airport, CargoCategory, Direction } from '../api/types';

/** Reference ma'lumot — server 1 soat keshlaydi, klient ham (§12). */
export function useReference() {
  return useQuery({
    queryKey: ['reference'],
    queryFn: () => api.reference(),
    staleTime: 60 * 60 * 1000,
  });
}

/** Yo'nalishga qarab chiqish/kelish aeroportlari. Mashhurlari tepada (§9.2). */
export function airportsFor(
  airports: Airport[] | undefined,
  direction: Direction | null,
  side: 'origin' | 'dest',
): Airport[] {
  if (!airports || !direction) {
    return [];
  }
  const originCountry = direction === 'JP_UZ' ? 'JP' : 'UZ';
  const destCountry = direction === 'JP_UZ' ? 'UZ' : 'JP';
  const country = side === 'origin' ? originCountry : destCountry;

  return airports
    .filter((airport) => airport.countryCode === country)
    .sort((a, b) => Number(b.popular) - Number(a.popular) || a.sortOrder - b.sortOrder);
}

export function categoryById(
  categories: CargoCategory[] | undefined,
  id: number,
): CargoCategory | undefined {
  return categories?.find((category) => category.id === id);
}

/** Tanlangan kategoriyalar orasidagi HIGH risklilar — ogohlantirish uchun (§7.3). */
export function highRiskWarnings(
  categories: CargoCategory[] | undefined,
  selectedIds: number[],
): CargoCategory[] {
  if (!categories) {
    return [];
  }
  return categories.filter(
    (category) =>
      selectedIds.includes(category.id) && category.riskLevel === 'HIGH' && category.warningUz,
  );
}
