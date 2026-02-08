-- Enable Supabase Realtime for all 7 sync tables.
-- Required for the sync engine's RealtimeManager to receive change events.
-- Run: SELECT * FROM pg_publication_tables WHERE pubname = 'supabase_realtime'; to verify.

ALTER PUBLICATION supabase_realtime ADD TABLE locations;
ALTER PUBLICATION supabase_realtime ADD TABLE products;
ALTER PUBLICATION supabase_realtime ADD TABLE expense_categories;
ALTER PUBLICATION supabase_realtime ADD TABLE purchase_batches;
ALTER PUBLICATION supabase_realtime ADD TABLE sale_batches;
ALTER PUBLICATION supabase_realtime ADD TABLE transactions;
ALTER PUBLICATION supabase_realtime ADD TABLE cash_operations;

-- Set REPLICA IDENTITY FULL so UPDATE/DELETE events include the complete row.
-- Without this, Realtime only sends the PK for updates, breaking applyToRoom.
ALTER TABLE locations REPLICA IDENTITY FULL;
ALTER TABLE products REPLICA IDENTITY FULL;
ALTER TABLE expense_categories REPLICA IDENTITY FULL;
ALTER TABLE purchase_batches REPLICA IDENTITY FULL;
ALTER TABLE sale_batches REPLICA IDENTITY FULL;
ALTER TABLE transactions REPLICA IDENTITY FULL;
ALTER TABLE cash_operations REPLICA IDENTITY FULL;
