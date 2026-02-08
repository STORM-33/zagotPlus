export interface QueryResult {
  rows: Record<string, unknown>[];
  rowCount: number;
}

export interface ParsedResponse {
  type: 'direct' | 'query';
  text?: string;
  query?: string;
  explanation?: string;
}

export interface ValidationResult {
  valid: boolean;
  reason?: string;
}
