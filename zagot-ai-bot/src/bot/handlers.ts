import { Context } from 'grammy';
import { askClaude } from '../ai/claude.js';
import { parseClaudeResponse } from '../ai/parser.js';
import { executeQuery, validateQuery } from '../db/queries.js';
import { formatResults } from '../utils/formatter.js';
import { logger } from '../utils/logger.js';

export async function handleMessage(ctx: Context): Promise<void> {
  const text = ctx.message?.text;
  if (!text) return;

  const userId = ctx.from?.id?.toString() || 'unknown';

  // Show typing indicator
  await ctx.replyWithChatAction('typing');

  try {
    // Get Claude's response
    const claudeResponse = await askClaude(text);
    const parsed = parseClaudeResponse(claudeResponse);

    if (parsed.type === 'direct') {
      await ctx.reply(parsed.text || 'Не вдалося отримати відповідь');
      return;
    }

    // Validate SQL
    const validation = validateQuery(parsed.query!);
    if (!validation.valid) {
      logger.warn({ query: parsed.query, reason: validation.reason }, 'Query rejected');
      await ctx.reply(`❌ Запит відхилено: ${validation.reason}`);
      return;
    }

    // Execute query
    logger.info({ query: parsed.query }, 'Executing query');
    const results = await executeQuery(parsed.query!);

    // Format and send results
    const formatted = formatResults(results, parsed.explanation);
    await ctx.reply(formatted, { parse_mode: 'HTML' });
  } catch (error) {
    logger.error({ error, text, userId }, 'Handler error');

    // Provide user-friendly error message
    if (error instanceof Error) {
      if (error.message.includes('timeout')) {
        await ctx.reply('⏱️ Запит виконувався занадто довго. Спробуйте спростити питання.');
      } else if (error.message.includes('connect')) {
        await ctx.reply('🔌 Помилка з\'єднання з базою даних. Спробуйте пізніше.');
      } else {
        await ctx.reply('❌ Помилка обробки запиту. Спробуйте ще раз.');
      }
    } else {
      await ctx.reply('❌ Помилка обробки запиту. Спробуйте ще раз.');
    }
  }
}

export async function handleStart(ctx: Context): Promise<void> {
  await ctx.reply(
    `👋 Привіт! Я AI-асистент Zagot+.

Я можу відповісти на питання про:
• 📦 Залишки товарів
• 🛒 Закупки та продажі
• 💰 Баланс каси
• 📊 Статистику по товарах

Просто напишіть ваше питання українською, наприклад:
"Скільки товару на складі?"
"Які закупки були сьогодні?"
"Який загальний баланс каси?"`,
    { parse_mode: 'HTML' }
  );
}

export async function handleHelp(ctx: Context): Promise<void> {
  await ctx.reply(
    `📖 <b>Допомога</b>

<b>Приклади питань:</b>
• "Покажи залишки на складі"
• "Скільки закупили горіха за тиждень?"
• "Який товар найприбутковіший?"
• "Продажі за сьогодні по локаціях"
• "Загальний баланс каси"

<b>Підказки:</b>
• Можете вказувати період: "за вчора", "за тиждень", "за місяць"
• Можете фільтрувати по локації: "на Кіоску", "на Складі"
• Якщо питання незрозуміле, я попрошу уточнити`,
    { parse_mode: 'HTML' }
  );
}
