import type { QueryResult } from '../types/index.js';

const MONEY_COLUMNS = ['amount', 'price', 'balance', 'profit', 'total', 'sum'];
const WEIGHT_COLUMNS = ['kg', 'weight', 'quantity'];

function isMoneyColumn(columnName: string): boolean {
  const lower = columnName.toLowerCase();
  return MONEY_COLUMNS.some((m) => lower.includes(m));
}

function isWeightColumn(columnName: string): boolean {
  const lower = columnName.toLowerCase();
  return WEIGHT_COLUMNS.some((w) => lower.includes(w));
}

export function formatResults(result: QueryResult, explanation?: string): string {
  const parts: string[] = [];

  // Add explanation if present
  if (explanation) {
    parts.push(`📊 ${explanation}\n`);
  }

  if (result.rowCount === 0) {
    parts.push('Нічого не знайдено.');
    return parts.join('\n');
  }

  // Single value result (COUNT, SUM, etc.)
  if (result.rowCount === 1 && Object.keys(result.rows[0]).length === 1) {
    const [columnName, value] = Object.entries(result.rows[0])[0];
    parts.push(`<b>${formatValue(value, columnName)}</b>`);
    return parts.join('\n');
  }

  // Multiple rows - format as list
  for (const row of result.rows.slice(0, 20)) {
    const line = formatRow(row);
    parts.push(line);
  }

  if (result.rowCount > 20) {
    parts.push(`\n... та ще ${result.rowCount - 20} записів`);
  }

  // Truncate if too long for Telegram
  let output = parts.join('\n');
  if (output.length > 4000) {
    output = output.slice(0, 3900) + '\n\n... (обрізано)';
  }

  return output;
}

function formatRow(row: Record<string, unknown>): string {
  const entries = Object.entries(row);

  // If row has 'name' or similar field, use it as header
  const nameField = entries.find(
    ([key]) =>
      key === 'name' ||
      key === 'product' ||
      key === 'location' ||
      key.includes('name') ||
      key.includes('назва')
  );

  if (nameField) {
    const otherFields = entries
      .filter(([key]) => key !== nameField[0])
      .map(([key, value]) => formatValue(value, key))
      .join(' • ');

    return `• <b>${nameField[1]}</b>: ${otherFields}`;
  }

  // Generic formatting
  return '• ' + entries.map(([key, value]) => formatValue(value, key)).join(' • ');
}

function formatValue(value: unknown, columnName?: string): string {
  if (value === null || value === undefined) return '—';

  // Parse numeric string from postgres
  let num: number | null = null;
  if (typeof value === 'number') {
    num = value;
  } else if (typeof value === 'string' && /^-?\d+\.?\d*$/.test(value)) {
    num = parseFloat(value);
    if (isNaN(num)) num = null;
  }

  if (num !== null && columnName) {
    // Money columns
    if (isMoneyColumn(columnName)) {
      return `₴${num.toLocaleString('uk-UA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }

    // Weight columns
    if (isWeightColumn(columnName)) {
      return `${num.toFixed(2)} кг`;
    }

    // Large numbers without unit
    if (Math.abs(num) >= 1000) {
      return num.toLocaleString('uk-UA', { maximumFractionDigits: 2 });
    }

    // Small numbers
    return num.toFixed(Number.isInteger(num) ? 0 : 2);
  }

  if (num !== null) {
    // No column context - just format the number
    if (Math.abs(num) >= 1000) {
      return num.toLocaleString('uk-UA', { maximumFractionDigits: 2 });
    }
    return num.toFixed(Number.isInteger(num) ? 0 : 2);
  }

  if (value instanceof Date) {
    return value.toLocaleDateString('uk-UA');
  }

  return String(value);
}
