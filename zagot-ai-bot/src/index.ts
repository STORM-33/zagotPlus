import dns from 'node:dns';
dns.setDefaultResultOrder('ipv4first');

import { startBot } from './bot/bot.js';
import { logger } from './utils/logger.js';
import { sql } from './db/client.js';

async function main() {
  logger.info('Zagot+ AI Bot starting...');

  // Test database connection
  try {
    const result = await sql`SELECT 1 as test`;
    logger.info({ result: result[0] }, 'Database connection successful');
  } catch (error) {
    logger.error({ error }, 'Database connection failed');
    process.exit(1);
  }

  // Start the bot
  await startBot();
}

// Graceful shutdown
process.on('SIGINT', async () => {
  logger.info('Shutting down...');
  await sql.end();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  logger.info('Shutting down...');
  await sql.end();
  process.exit(0);
});

main().catch((error) => {
  logger.error({ error }, 'Fatal error');
  process.exit(1);
});
