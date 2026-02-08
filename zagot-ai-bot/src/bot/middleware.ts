import { Context, NextFunction } from 'grammy';
import { config } from '../utils/config.js';
import { logger } from '../utils/logger.js';

const ALLOWED_USERS = new Set(config.ALLOWED_TELEGRAM_IDS);
const COOLDOWN_MS = 2000;
const lastRequest = new Map<string, number>();

export async function authMiddleware(ctx: Context, next: NextFunction): Promise<void> {
  const userId = ctx.from?.id?.toString();

  if (!userId || !ALLOWED_USERS.has(userId)) {
    logger.warn({ userId }, 'Unauthorized access attempt');
    await ctx.reply('⛔ Доступ заборонено');
    return;
  }

  await next();
}

export async function rateLimitMiddleware(ctx: Context, next: NextFunction): Promise<void> {
  const userId = ctx.from?.id?.toString();
  if (!userId) {
    await next();
    return;
  }

  const now = Date.now();
  const last = lastRequest.get(userId) || 0;

  if (now - last < COOLDOWN_MS) {
    await ctx.reply('⏳ Зачекайте кілька секунд...');
    return;
  }

  lastRequest.set(userId, now);
  await next();
}

export async function loggingMiddleware(ctx: Context, next: NextFunction): Promise<void> {
  const startTime = Date.now();
  const userId = ctx.from?.id?.toString() || 'unknown';
  const text = ctx.message?.text || '';

  logger.info({ userId, text: text.slice(0, 100) }, 'Incoming message');

  await next();

  const duration = Date.now() - startTime;
  logger.info({ userId, duration }, 'Request completed');
}
