import { describe, expect, it } from 'vitest';
import { query } from './client';

describe('query', () => {
  it("bo'sh qiymatlarni tashlab ketadi", () => {
    expect(query({ status: 'PENDING', type: '', page: undefined, size: null })).toBe(
      '?status=PENDING',
    );
  });

  it('hech narsa qolmasa bo\u2019sh satr qaytaradi', () => {
    expect(query({ a: '', b: undefined })).toBe('');
  });

  it('raqam va booleanni ham qo\u2019shadi', () => {
    const result = query({ page: 0, priorityFirst: true });
    expect(result).toContain('page=0');
    expect(result).toContain('priorityFirst=true');
  });

  it('maxsus belgilarni kodlaydi', () => {
    expect(query({ search: 'a b&c' })).toBe('?search=a+b%26c');
  });
});
