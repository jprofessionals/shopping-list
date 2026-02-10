import { describe, it, expect } from 'vitest';
import { parseItemInput, formatParsedItem, type ParsedItem } from './parseItemInput';

describe('parseItemInput', () => {
  describe('quantity and unit at start', () => {
    it('parses "2kg potatoes"', () => {
      const result = parseItemInput('2kg potatoes');
      expect(result).toEqual({
        name: 'Potatoes',
        quantity: 2,
        unit: 'kg',
      });
    });

    it('parses "2 kg potatoes" (with space)', () => {
      const result = parseItemInput('2 kg potatoes');
      expect(result).toEqual({
        name: 'Potatoes',
        quantity: 2,
        unit: 'kg',
      });
    });

    it('parses "500g flour"', () => {
      const result = parseItemInput('500g flour');
      expect(result).toEqual({
        name: 'Flour',
        quantity: 500,
        unit: 'g',
      });
    });

    it('parses "1.5 kg chicken"', () => {
      const result = parseItemInput('1.5 kg chicken');
      expect(result).toEqual({
        name: 'Chicken',
        quantity: 1.5,
        unit: 'kg',
      });
    });

    it('parses "2l milk"', () => {
      const result = parseItemInput('2l milk');
      expect(result).toEqual({
        name: 'Milk',
        quantity: 2,
        unit: 'l',
      });
    });

    it('parses "500ml cream"', () => {
      const result = parseItemInput('500ml cream');
      expect(result).toEqual({
        name: 'Cream',
        quantity: 500,
        unit: 'ml',
      });
    });

    it('parses "3 bags chips"', () => {
      const result = parseItemInput('3 bags chips');
      expect(result).toEqual({
        name: 'Chips',
        quantity: 3,
        unit: 'bags',
      });
    });

    it('parses "1 dozen eggs"', () => {
      const result = parseItemInput('1 dozen eggs');
      expect(result).toEqual({
        name: 'Eggs',
        quantity: 1,
        unit: 'doz',
      });
    });
  });

  describe('quantity and unit at end', () => {
    it('parses "potatoes 2kg"', () => {
      const result = parseItemInput('potatoes 2kg');
      expect(result).toEqual({
        name: 'Potatoes',
        quantity: 2,
        unit: 'kg',
      });
    });

    it('parses "milk 2 l" (with space)', () => {
      const result = parseItemInput('milk 2 l');
      expect(result).toEqual({
        name: 'Milk',
        quantity: 2,
        unit: 'l',
      });
    });

    it('parses "bread 1 loaf"', () => {
      const result = parseItemInput('bread 1 loaf');
      expect(result).toEqual({
        name: 'Bread',
        quantity: 1,
        unit: 'loaf',
      });
    });

    it('parses "olive oil 500ml"', () => {
      const result = parseItemInput('olive oil 500ml');
      expect(result).toEqual({
        name: 'Olive oil',
        quantity: 500,
        unit: 'ml',
      });
    });
  });

  describe('quantity only (no unit)', () => {
    it('parses "3 apples"', () => {
      const result = parseItemInput('3 apples');
      expect(result).toEqual({
        name: 'Apples',
        quantity: 3,
        unit: null,
      });
    });

    it('parses "apples 3"', () => {
      const result = parseItemInput('apples 3');
      expect(result).toEqual({
        name: 'Apples',
        quantity: 3,
        unit: null,
      });
    });

    it('parses "6 bananas"', () => {
      const result = parseItemInput('6 bananas');
      expect(result).toEqual({
        name: 'Bananas',
        quantity: 6,
        unit: null,
      });
    });

    it('parses "12 eggs"', () => {
      const result = parseItemInput('12 eggs');
      expect(result).toEqual({
        name: 'Eggs',
        quantity: 12,
        unit: null,
      });
    });
  });

  describe('name only (no quantity or unit)', () => {
    it('parses "bread"', () => {
      const result = parseItemInput('bread');
      expect(result).toEqual({
        name: 'Bread',
        quantity: null,
        unit: null,
      });
    });

    it('parses "olive oil"', () => {
      const result = parseItemInput('olive oil');
      expect(result).toEqual({
        name: 'Olive oil',
        quantity: null,
        unit: null,
      });
    });

    it('parses "chocolate chip cookies"', () => {
      const result = parseItemInput('chocolate chip cookies');
      expect(result).toEqual({
        name: 'Chocolate chip cookies',
        quantity: null,
        unit: null,
      });
    });
  });

  describe('edge cases', () => {
    it('handles empty string', () => {
      const result = parseItemInput('');
      expect(result).toEqual({
        name: '',
        quantity: null,
        unit: null,
      });
    });

    it('handles whitespace only', () => {
      const result = parseItemInput('   ');
      expect(result).toEqual({
        name: '',
        quantity: null,
        unit: null,
      });
    });

    it('trims whitespace from input', () => {
      const result = parseItemInput('  2kg potatoes  ');
      expect(result).toEqual({
        name: 'Potatoes',
        quantity: 2,
        unit: 'kg',
      });
    });

    it('preserves existing capitalization after first letter', () => {
      const result = parseItemInput('iPhones');
      expect(result.name).toBe('IPhones');
    });

    it('handles decimal quantities', () => {
      const result = parseItemInput('0.5kg butter');
      expect(result).toEqual({
        name: 'Butter',
        quantity: 0.5,
        unit: 'kg',
      });
    });
  });

  describe('unit normalization', () => {
    it('normalizes "grams" to "g"', () => {
      const result = parseItemInput('500 grams sugar');
      expect(result.unit).toBe('g');
    });

    it('normalizes "liters" to "l"', () => {
      const result = parseItemInput('2 liters juice');
      expect(result.unit).toBe('l');
    });

    it('normalizes "litres" (British) to "l"', () => {
      const result = parseItemInput('2 litres juice');
      expect(result.unit).toBe('l');
    });

    it('normalizes "pounds" to "lb"', () => {
      const result = parseItemInput('2 pounds beef');
      expect(result.unit).toBe('lb');
    });

    it('normalizes "pieces" to "pc"', () => {
      const result = parseItemInput('5 pieces candy');
      expect(result.unit).toBe('pc');
    });

    it('normalizes "dozen" to "doz"', () => {
      const result = parseItemInput('1 dozen eggs');
      expect(result.unit).toBe('doz');
    });

    it('handles case-insensitive units', () => {
      const result1 = parseItemInput('2KG potatoes');
      const result2 = parseItemInput('2Kg potatoes');
      expect(result1.unit).toBe('kg');
      expect(result2.unit).toBe('kg');
    });
  });

  describe('various units', () => {
    const testCases: { input: string; expectedUnit: string }[] = [
      { input: '2 cups flour', expectedUnit: 'cups' },
      { input: '1 tbsp oil', expectedUnit: 'tbsp' },
      { input: '2 tsp salt', expectedUnit: 'tsp' },
      { input: '3 packs gum', expectedUnit: 'packs' },
      { input: '2 bottles water', expectedUnit: 'bottles' },
      { input: '1 jar pickles', expectedUnit: 'jar' },
      { input: '2 cans beans', expectedUnit: 'cans' },
      { input: '1 box cereal', expectedUnit: 'box' },
      { input: '1 bunch bananas', expectedUnit: 'bunch' },
      { input: '2 slices bread', expectedUnit: 'slices' },
      { input: '1 carton milk', expectedUnit: 'carton' },
    ];

    testCases.forEach(({ input, expectedUnit }) => {
      it(`parses "${input}" with unit "${expectedUnit}"`, () => {
        const result = parseItemInput(input);
        expect(result.unit).toBe(expectedUnit);
        expect(result.quantity).not.toBeNull();
      });
    });
  });
});

describe('formatParsedItem', () => {
  it('formats item with quantity and unit', () => {
    const parsed: ParsedItem = { name: 'Potatoes', quantity: 2, unit: 'kg' };
    expect(formatParsedItem(parsed)).toBe('Potatoes - 2 kg');
  });

  it('formats item with quantity only', () => {
    const parsed: ParsedItem = { name: 'Apples', quantity: 3, unit: null };
    expect(formatParsedItem(parsed)).toBe('Apples - 3');
  });

  it('formats item with name only', () => {
    const parsed: ParsedItem = { name: 'Bread', quantity: null, unit: null };
    expect(formatParsedItem(parsed)).toBe('Bread');
  });

  it('handles empty name', () => {
    const parsed: ParsedItem = { name: '', quantity: null, unit: null };
    expect(formatParsedItem(parsed)).toBe('');
  });

  it('formats decimal quantities', () => {
    const parsed: ParsedItem = { name: 'Butter', quantity: 0.5, unit: 'kg' };
    expect(formatParsedItem(parsed)).toBe('Butter - 0.5 kg');
  });
});
