import Anthropic from '@anthropic-ai/sdk';
import { config } from '../utils/config.js';
import { logger } from '../utils/logger.js';
import { buildSystemPrompt } from './prompts.js';

const anthropic = new Anthropic({
  apiKey: config.ANTHROPIC_API_KEY,
});

export async function askClaude(userMessage: string): Promise<string> {
  const startTime = Date.now();

  try {
    const response = await anthropic.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 1024,
      system: buildSystemPrompt(),
      messages: [
        {
          role: 'user',
          content: userMessage,
        },
      ],
    });

    const latencyMs = Date.now() - startTime;

    // Extract text from response
    const textContent = response.content.find((block) => block.type === 'text');
    const text = textContent?.type === 'text' ? textContent.text : '';

    logger.info(
      {
        latencyMs,
        inputTokens: response.usage.input_tokens,
        outputTokens: response.usage.output_tokens,
      },
      'Claude response received'
    );

    return text;
  } catch (error) {
    logger.error({ error }, 'Claude API error');
    throw error;
  }
}
