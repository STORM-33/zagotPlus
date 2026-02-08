import 'dotenv/config';
import { z } from 'zod';

const configSchema = z.object({
  TELEGRAM_BOT_TOKEN: z.string().min(1, 'TELEGRAM_BOT_TOKEN is required'),
  ALLOWED_TELEGRAM_IDS: z.string().transform((s) => s.split(',').map((id) => id.trim())),
  DATABASE_URL_READONLY: z.string().min(1, 'DATABASE_URL_READONLY is required'),
  ANTHROPIC_API_KEY: z.string().min(1, 'ANTHROPIC_API_KEY is required'),
  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
});

export const config = configSchema.parse(process.env);
