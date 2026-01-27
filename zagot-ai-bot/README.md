# Zagot+ AI Telegram Bot

Telegram бот для запитів до бази даних Zagot+ за допомогою Claude AI.

## Швидкий старт

1. **Встановити залежності:**
   ```bash
   npm install
   ```

2. **Налаштувати змінні середовища:**
   ```bash
   cp .env.example .env
   # Відредагуйте .env файл
   ```

3. **Запустити в режимі розробки:**
   ```bash
   npm run dev
   ```

4. **Для продакшн:**
   ```bash
   npm run build
   npm start
   ```

## Змінні середовища

| Змінна | Опис |
|--------|------|
| `TELEGRAM_BOT_TOKEN` | Токен бота від @BotFather |
| `ALLOWED_TELEGRAM_IDS` | ID дозволених користувачів (через кому) |
| `DATABASE_URL_READONLY` | URL підключення до бази (read-only) |
| `ANTHROPIC_API_KEY` | API ключ Claude |
| `LOG_LEVEL` | Рівень логування (debug/info/warn/error) |

## Приклади запитів

- "Скільки товару на складі?"
- "Покажи закупки за сьогодні"
- "Який товар найприбутковіший?"
- "Загальний баланс каси"
- "Продажі за тиждень"

## Архітектура

```
src/
├── index.ts          # Точка входу
├── bot/              # Telegram bot (Grammy)
│   ├── bot.ts
│   ├── handlers.ts
│   └── middleware.ts
├── ai/               # Claude інтеграція
│   ├── claude.ts
│   ├── prompts.ts
│   └── parser.ts
├── db/               # База даних
│   ├── client.ts
│   ├── schema.ts
│   └── queries.ts
├── utils/            # Утиліти
│   ├── config.ts
│   ├── formatter.ts
│   └── logger.ts
└── types/            # TypeScript типи
    └── index.ts
```

## Безпека

- Використовується read-only роль для бази даних
- Всі SQL запити валідуються перед виконанням
- Доступ обмежений списком дозволених Telegram ID
- Заборонені DDL та DML операції
