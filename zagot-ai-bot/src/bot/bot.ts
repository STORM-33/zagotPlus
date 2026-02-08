import { Bot } from 'grammy';
import { config } from '../utils/config.js';
import { logger } from '../utils/logger.js';
import { authMiddleware, loggingMiddleware, rateLimitMiddleware } from './middleware.js';
import { handleMessage, handleStart, handleHelp } from './handlers.js';

export const bot = new Bot(config.TELEGRAM_BOT_TOKEN);

// Middleware
bot.use(loggingMiddleware);
bot.use(authMiddleware);
bot.use(rateLimitMiddleware);

// Commands
bot.command('start', handleStart);
bot.command('help', handleHelp);

// Message handler
bot.on('message:text', handleMessage);

// Error handling
bot.catch((err) => {
  logger.error({ error: err.error, ctx: err.ctx?.update }, 'Bot error');
});

export async function startBot(): Promise<void> {
  logger.info('Starting bot...');

  // Use long polling for development, webhooks for production
  await bot.start({
    onStart: (info) => {
      logger.info({ username: info.username }, 'Bot started');
    },
  });
}
