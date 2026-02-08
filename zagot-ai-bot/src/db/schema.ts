export const SCHEMA_DESCRIPTION = `
TABLES:

products (товари)
- id: UUID
- name: TEXT (назва товару)
- default_buy_price: DECIMAL (ціна закупки за кг)
- default_sell_price: DECIMAL (ціна продажу за кг)
- is_active: BOOLEAN
- created_at: TIMESTAMPTZ

locations (локації)
- id: UUID
- name: TEXT (Кіоск, Склад, тощо)
- type: TEXT (kiosk, mobile)
- created_at: TIMESTAMPTZ

transactions (транзакції - рух товару)
- id: UUID
- local_id: TEXT
- location_id: UUID → locations
- type: TEXT (purchase, sale, transfer_out, transfer_in, adjustment)
- product_id: UUID → products
- weight_kg: DECIMAL (вага в кг)
- price_per_kg: DECIMAL
- total_amount: DECIMAL
- batch_id: UUID → purchase_batches (для закупок)
- sale_batch_id: UUID → sale_batches (для продажів)
- created_at: TIMESTAMPTZ

purchase_batches (партії закупок)
- id: UUID
- location_id: UUID → locations
- total_weight_kg: DECIMAL
- total_amount: DECIMAL
- item_count: INTEGER
- is_voided: BOOLEAN (скасована партія)
- corrects_batch_id: UUID (посилання на оригінальну партію при корекції)
- correction_reason: TEXT
- created_at: TIMESTAMPTZ

sale_batches (партії продажів)
- id: UUID
- location_id: UUID → locations
- total_weight_kg: DECIMAL
- total_amount: DECIMAL
- item_count: INTEGER
- is_voided: BOOLEAN (скасована партія)
- corrects_batch_id: UUID (посилання на оригінальну партію при корекції)
- correction_reason: TEXT
- created_at: TIMESTAMPTZ

cash_operations (касові операції)
- id: UUID
- location_id: UUID → locations
- type: TEXT (deposit, withdrawal, payment, purchase)
- amount: DECIMAL
- batch_id: UUID → purchase_batches
- category_id: UUID → expense_categories
- notes: TEXT
- is_transfer: BOOLEAN (операція переказу між локаціями)
- transfer_pair_id: TEXT (пара для переказу)
- created_at: TIMESTAMPTZ

expense_categories (категорії витрат)
- id: UUID
- name: TEXT
- is_active: BOOLEAN
- created_at: TIMESTAMPTZ

VIEWS (готові запити):

inventory (поточні залишки - використовуй цей view!)
- location_id: UUID
- product_id: UUID
- quantity_kg: DECIMAL (поточна вага на локації, враховує voided)

cash_balance (баланс каси по локаціях)
- location_id: UUID
- balance: DECIMAL

total_cash_balance (загальний баланс каси)
- balance: DECIMAL (одне значення - сума по всіх локаціях)

ВАЖЛИВО:
- Views автоматично виключають voided партії!
- Для залишків ЗАВЖДИ використовуй view "inventory", не рахуй вручну
- Для балансу каси використовуй "cash_balance" або "total_cash_balance"

COMMON QUERIES (приклади):

Залишки на всіх локаціях:
SELECT l.name as location, p.name as product, i.quantity_kg 
FROM inventory i 
JOIN products p ON p.id = i.product_id 
JOIN locations l ON l.id = i.location_id 
WHERE i.quantity_kg > 0
ORDER BY l.name, p.name

Закупки за сьогодні:
SELECT p.name, t.weight_kg, t.total_amount, l.name as location
FROM transactions t 
JOIN products p ON p.id = t.product_id 
JOIN locations l ON l.id = t.location_id
WHERE t.type = 'purchase' 
AND t.created_at >= CURRENT_DATE
AND (t.batch_id IS NULL OR NOT EXISTS (
  SELECT 1 FROM purchase_batches pb WHERE pb.id = t.batch_id AND pb.is_voided = true
))
ORDER BY t.created_at DESC

Продажі за період:
SELECT p.name, SUM(t.weight_kg) as total_kg, SUM(t.total_amount) as total_sum
FROM transactions t 
JOIN products p ON p.id = t.product_id
WHERE t.type = 'sale' 
AND t.created_at >= CURRENT_DATE - INTERVAL '7 days'
AND (t.sale_batch_id IS NULL OR NOT EXISTS (
  SELECT 1 FROM sale_batches sb WHERE sb.id = t.sale_batch_id AND sb.is_voided = true
))
GROUP BY p.name
ORDER BY total_sum DESC

Очікуваний прибуток (по залишках):
SELECT p.name, i.quantity_kg, 
  (p.default_sell_price - p.default_buy_price) * i.quantity_kg as expected_profit
FROM inventory i 
JOIN products p ON p.id = i.product_id
WHERE i.quantity_kg > 0
ORDER BY expected_profit DESC

Баланс каси по локаціях:
SELECT l.name as location, cb.balance
FROM cash_balance cb
JOIN locations l ON l.id = cb.location_id

Загальний баланс каси:
SELECT balance FROM total_cash_balance
`;
