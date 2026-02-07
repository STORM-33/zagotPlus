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
