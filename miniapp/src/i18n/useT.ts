import { useLanguage } from '../store/appStore';
import { dictionary, type Dictionary } from './index';

/** Joriy tildagi lug'at. */
export function useT(): Dictionary {
  return dictionary(useLanguage());
}
