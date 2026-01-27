import type { ParsedResponse } from '../types/index.js';

export function parseClaudeResponse(response: string): ParsedResponse {
  // Look for SQL JSON block
  const sqlMatch = response.match(/```sql\n(\{[\s\S]*?\})\n```/);

  if (sqlMatch) {
    try {
      const parsed = JSON.parse(sqlMatch[1]) as { query?: string; explanation?: string };
      if (parsed.query) {
        return {
          type: 'query',
          query: parsed.query,
          explanation: parsed.explanation,
        };
      }
    } catch {
      // Failed to parse JSON, treat as direct response
    }
  }

  // Also try to match just the JSON without sql fence
  const jsonMatch = response.match(/\{"query":\s*"[\s\S]*?"\}/);
  if (jsonMatch) {
    try {
      const parsed = JSON.parse(jsonMatch[0]) as { query?: string; explanation?: string };
      if (parsed.query) {
        return {
          type: 'query',
          query: parsed.query,
          explanation: parsed.explanation,
        };
      }
    } catch {
      // Failed to parse, treat as direct response
    }
  }

  return {
    type: 'direct',
    text: response,
  };
}
