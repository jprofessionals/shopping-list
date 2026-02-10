/**
 * Parsed result from item input text
 */
export interface ParsedItem {
  name: string;
  quantity: number | null;
  unit: string | null;
}

/**
 * Common unit patterns (case-insensitive)
 * Includes both abbreviated and full forms
 */
const UNIT_PATTERNS: string[] = [
  // Weight
  'kg',
  'g',
  'gram',
  'grams',
  'lb',
  'lbs',
  'pound',
  'pounds',
  'oz',
  'ounce',
  'ounces',
  // Volume
  'l',
  'liter',
  'liters',
  'litre',
  'litres',
  'ml',
  'milliliter',
  'milliliters',
  'dl',
  'cl',
  'gal',
  'gallon',
  'gallons',
  'pt',
  'pint',
  'pints',
  'qt',
  'quart',
  'quarts',
  'cup',
  'cups',
  'tbsp',
  'tablespoon',
  'tablespoons',
  'tsp',
  'teaspoon',
  'teaspoons',
  // Count/packaging
  'pc',
  'pcs',
  'piece',
  'pieces',
  'pack',
  'packs',
  'packet',
  'packets',
  'bag',
  'bags',
  'box',
  'boxes',
  'can',
  'cans',
  'bottle',
  'bottles',
  'jar',
  'jars',
  'carton',
  'cartons',
  'bunch',
  'bunches',
  'dozen',
  'doz',
  'slice',
  'slices',
  'loaf',
  'loaves',
];

// Build regex pattern for units (case-insensitive)
const unitPattern = UNIT_PATTERNS.join('|');

/**
 * Patterns to match quantity and unit combinations
 * Handles various formats like:
 * - "2kg potatoes" or "2 kg potatoes"
 * - "potatoes 2kg" or "potatoes 2 kg"
 * - "3 apples" (quantity only, no unit)
 * - "milk 2l" or "2l milk"
 */

// Pattern: quantity + optional unit at START (e.g., "2kg potatoes", "2 kg potatoes", "3 apples")
const QUANTITY_AT_START_REGEX = new RegExp(
  `^(\\d+(?:\\.\\d+)?)\\s*(${unitPattern})?\\s+(.+)$`,
  'i'
);

// Pattern: quantity + optional unit at END (e.g., "potatoes 2kg", "milk 2 l")
const QUANTITY_AT_END_REGEX = new RegExp(`^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(${unitPattern})?$`, 'i');

// Pattern: just a number at the start followed by item name (e.g., "3 apples")
const NUMBER_AT_START_REGEX = /^(\d+(?:\.\d+)?)\s+(.+)$/;

// Pattern: just a number at the end (e.g., "apples 3")
const NUMBER_AT_END_REGEX = /^(.+?)\s+(\d+(?:\.\d+)?)$/;

/**
 * Normalize unit to a standard form
 * e.g., "kilograms" -> "kg", "liters" -> "l"
 */
function normalizeUnit(unit: string): string {
  const lower = unit.toLowerCase();

  // Weight normalizations
  if (['gram', 'grams'].includes(lower)) return 'g';
  if (['kilogram', 'kilograms'].includes(lower)) return 'kg';
  if (['pound', 'pounds', 'lbs'].includes(lower)) return 'lb';
  if (['ounce', 'ounces'].includes(lower)) return 'oz';

  // Volume normalizations
  if (['liter', 'liters', 'litre', 'litres'].includes(lower)) return 'l';
  if (['milliliter', 'milliliters'].includes(lower)) return 'ml';
  if (['gallon', 'gallons'].includes(lower)) return 'gal';
  if (['pint', 'pints'].includes(lower)) return 'pt';
  if (['quart', 'quarts'].includes(lower)) return 'qt';
  if (['tablespoon', 'tablespoons'].includes(lower)) return 'tbsp';
  if (['teaspoon', 'teaspoons'].includes(lower)) return 'tsp';

  // Count normalizations
  if (['piece', 'pieces', 'pcs'].includes(lower)) return 'pc';
  if (['packet', 'packets'].includes(lower)) return 'pack';
  if (['dozen'].includes(lower)) return 'doz';
  if (['loaves'].includes(lower)) return 'loaf';
  if (['bunches'].includes(lower)) return 'bunch';
  if (['boxes'].includes(lower)) return 'box';

  return lower;
}

/**
 * Capitalize the first letter of a string
 */
function capitalizeFirst(str: string): string {
  if (!str) return str;
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Parse user input into structured item data
 *
 * Examples:
 * - "2kg potatoes" -> { name: "Potatoes", quantity: 2, unit: "kg" }
 * - "3 apples" -> { name: "Apples", quantity: 3, unit: null }
 * - "milk 2l" -> { name: "Milk", quantity: 2, unit: "l" }
 * - "bread" -> { name: "Bread", quantity: null, unit: null }
 * - "1.5 kg chicken" -> { name: "Chicken", quantity: 1.5, unit: "kg" }
 */
export function parseItemInput(input: string): ParsedItem {
  const trimmed = input.trim();

  if (!trimmed) {
    return { name: '', quantity: null, unit: null };
  }

  // Try pattern: quantity + unit at start (e.g., "2kg potatoes", "2 kg potatoes")
  let match = trimmed.match(QUANTITY_AT_START_REGEX);
  if (match) {
    const [, qty, unit, name] = match;
    return {
      name: capitalizeFirst(name.trim()),
      quantity: parseFloat(qty),
      unit: unit ? normalizeUnit(unit) : null,
    };
  }

  // Try pattern: quantity + unit at end (e.g., "potatoes 2kg", "milk 2 l")
  match = trimmed.match(QUANTITY_AT_END_REGEX);
  if (match) {
    const [, name, qty, unit] = match;
    return {
      name: capitalizeFirst(name.trim()),
      quantity: parseFloat(qty),
      unit: unit ? normalizeUnit(unit) : null,
    };
  }

  // Try pattern: just number at start (e.g., "3 apples")
  match = trimmed.match(NUMBER_AT_START_REGEX);
  if (match) {
    const [, qty, name] = match;
    return {
      name: capitalizeFirst(name.trim()),
      quantity: parseFloat(qty),
      unit: null,
    };
  }

  // Try pattern: just number at end (e.g., "apples 3")
  match = trimmed.match(NUMBER_AT_END_REGEX);
  if (match) {
    const [, name, qty] = match;
    return {
      name: capitalizeFirst(name.trim()),
      quantity: parseFloat(qty),
      unit: null,
    };
  }

  // No quantity/unit found, just return the name
  return {
    name: capitalizeFirst(trimmed),
    quantity: null,
    unit: null,
  };
}

/**
 * Format a parsed item for display preview
 * e.g., { name: "Potatoes", quantity: 2, unit: "kg" } -> "Potatoes - 2 kg"
 */
export function formatParsedItem(parsed: ParsedItem): string {
  if (!parsed.name) return '';

  const parts = [parsed.name];

  if (parsed.quantity !== null) {
    if (parsed.unit) {
      parts.push(`${parsed.quantity} ${parsed.unit}`);
    } else {
      parts.push(`${parsed.quantity}`);
    }
  }

  return parts.join(' - ');
}
