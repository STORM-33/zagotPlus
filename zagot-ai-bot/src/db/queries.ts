import { sql } from './client.js';
import { logger } from '../utils/logger.js';
import type { QueryResult, ValidationResult } from '../types/index.js';

const FORBIDDEN_PATTERNS = [
  /\bINSERT\b/i,
  /\bUPDATE\b/i,
  /\bDELETE\b/i,
  /\bDROP\b/i,
  /\bTRUNCATE\b/i,
  /\bALTER\b/i,
  /\bCREATE\b/i,
  /\bGRANT\b/i,
  /\bREVOKE\b/i,
  /\bEXEC\b/i,
  /\bEXECUTE\b/i,
  /\bCALL\b/i,
  /\bSET\b/i,
  /\bCOPY\b/i,
  /--;/, // Comment tricks
  /\/\*/, // Block comments
  /\bpg_/i, // System functions
  /\binformation_schema\b/i, // Schema inspection
];

export function validateQuery(query: string): ValidationResult {
  // Check forbidden patterns
  for (const pattern of FORBIDDEN_PATTERNS) {
    if (pattern.test(query)) {
      return { valid: false, reason: `Заборонений патерн: ${pattern}` };
    }
  }

  // Must start with SELECT or WITH (for CTEs)
  const trimmed = query.trim().toUpperCase();
  if (!trimmed.startsWith('SELECT') && !trimmed.startsWith('WITH')) {
    return { valid: false, reason: 'Запит повинен починатися з SELECT або WITH' };
  }

  // Check for multiple statements (semicolon followed by more SQL)
  const parts = query.split(';').filter((p) => p.trim().length > 0);
  if (parts.length > 1) {
    return { valid: false, reason: 'Дозволено тільки один запит' };
  }

  return { valid: true };
}

const QUERY_TIMEOUT_MS = 10000;

export async function executeQuery(query: string): Promise<QueryResult> {
  const startTime = Date.now();

  try {
    const queryPromise = sql.unsafe(query);
    const timeoutPromise = new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error('Query timeout')), QUERY_TIMEOUT_MS)
    );

    const result = await Promise.race([queryPromise, timeoutPromise]);
    const executionMs = Date.now() - startTime;

    logger.info({ query, rowCount: result.length, executionMs }, 'Query executed');

    return {
      rows: result as Record<string, unknown>[],
      rowCount: result.length,
    };
  } catch (error) {
    logger.error({ error, query }, 'Query execution failed');
    throw error;
  }
}
