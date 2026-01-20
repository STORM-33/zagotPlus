


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


COMMENT ON SCHEMA "public" IS 'standard public schema';



CREATE EXTENSION IF NOT EXISTS "pg_graphql" WITH SCHEMA "graphql";






CREATE EXTENSION IF NOT EXISTS "pg_stat_statements" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA "extensions";






CREATE EXTENSION IF NOT EXISTS "supabase_vault" WITH SCHEMA "vault";






CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA "extensions";






CREATE OR REPLACE FUNCTION "public"."update_server_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
begin
  new.server_updated_at = now();
  return new;
end;
$$;


ALTER FUNCTION "public"."update_server_updated_at"() OWNER TO "postgres";

SET default_tablespace = '';

SET default_table_access_method = "heap";


CREATE TABLE IF NOT EXISTS "public"."cash_operations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "local_id" "text" NOT NULL,
    "location_id" "uuid",
    "type" "text" NOT NULL,
    "amount" numeric(10,2) NOT NULL,
    "category_id" "uuid",
    "batch_id" "uuid",
    "notes" "text",
    "device_id" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "synced_at" timestamp with time zone,
    "server_updated_at" timestamp with time zone DEFAULT "now"(),
    "is_transfer" boolean DEFAULT false NOT NULL,
    CONSTRAINT "cash_operations_type_check" CHECK (("type" = ANY (ARRAY['deposit'::"text", 'withdrawal'::"text", 'payment'::"text", 'purchase'::"text"])))
);


ALTER TABLE "public"."cash_operations" OWNER TO "postgres";


COMMENT ON COLUMN "public"."cash_operations"."is_transfer" IS 'True if this operation is part of a cash transfer between locations';



CREATE TABLE IF NOT EXISTS "public"."purchase_batches" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "local_id" "text" NOT NULL,
    "location_id" "uuid",
    "notes" "text",
    "total_weight_kg" numeric(10,3),
    "total_amount" numeric(10,2),
    "item_count" integer,
    "device_id" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "synced_at" timestamp with time zone,
    "server_updated_at" timestamp with time zone DEFAULT "now"(),
    "is_voided" boolean DEFAULT false NOT NULL,
    "corrects_batch_id" "uuid",
    "correction_reason" "text",
    "voided_at" timestamp with time zone,
    "voided_by_device_id" "text"
);


ALTER TABLE "public"."purchase_batches" OWNER TO "postgres";


COMMENT ON COLUMN "public"."purchase_batches"."is_voided" IS 'True if this batch has been voided due to a correction';



COMMENT ON COLUMN "public"."purchase_batches"."corrects_batch_id" IS 'Reference to the original batch this one corrects';



COMMENT ON COLUMN "public"."purchase_batches"."correction_reason" IS 'User-provided reason for the correction';



CREATE OR REPLACE VIEW "public"."cash_balance" AS
 SELECT "location_id",
    "sum"(
        CASE
            WHEN ("type" = 'deposit'::"text") THEN "amount"
            WHEN ("type" = ANY (ARRAY['withdrawal'::"text", 'payment'::"text", 'purchase'::"text"])) THEN (- "amount")
            ELSE NULL::numeric
        END) AS "balance"
   FROM "public"."cash_operations" "co"
  WHERE (("batch_id" IS NULL) OR (NOT (EXISTS ( SELECT 1
           FROM "public"."purchase_batches" "pb"
          WHERE (("pb"."id" = "co"."batch_id") AND ("pb"."is_voided" = true))))))
  GROUP BY "location_id";


ALTER VIEW "public"."cash_balance" OWNER TO "postgres";


COMMENT ON VIEW "public"."cash_balance" IS 'Cash balance per location, excludes voided purchase batches';



CREATE TABLE IF NOT EXISTS "public"."expense_categories" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "local_id" "text" NOT NULL,
    "name" "text" NOT NULL,
    "is_active" boolean DEFAULT true,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "synced_at" timestamp with time zone,
    "server_updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."expense_categories" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."sale_batches" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "local_id" "text" NOT NULL,
    "location_id" "uuid",
    "notes" "text",
    "total_weight_kg" numeric(10,3),
    "total_amount" numeric(10,2),
    "item_count" integer,
    "device_id" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "synced_at" timestamp with time zone,
    "server_updated_at" timestamp with time zone DEFAULT "now"(),
    "is_voided" boolean DEFAULT false NOT NULL,
    "corrects_batch_id" "uuid",
    "correction_reason" "text",
    "voided_at" timestamp with time zone,
    "voided_by_device_id" "text"
);


ALTER TABLE "public"."sale_batches" OWNER TO "postgres";


COMMENT ON COLUMN "public"."sale_batches"."is_voided" IS 'True if this batch has been voided due to a correction';



COMMENT ON COLUMN "public"."sale_batches"."corrects_batch_id" IS 'Reference to the original batch this one corrects';



COMMENT ON COLUMN "public"."sale_batches"."correction_reason" IS 'User-provided reason for the correction';



CREATE TABLE IF NOT EXISTS "public"."transactions" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "local_id" "text" NOT NULL,
    "location_id" "uuid",
    "type" "text" NOT NULL,
    "transfer_location_id" "uuid",
    "product_id" "uuid",
    "weight_kg" numeric(10,3) NOT NULL,
    "price_per_kg" numeric(10,2),
    "total_amount" numeric(10,2),
    "notes" "text",
    "device_id" "text",
    "created_at" timestamp with time zone DEFAULT "now"(),
    "synced_at" timestamp with time zone,
    "batch_id" "uuid",
    "sale_batch_id" "uuid",
    "server_updated_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "transactions_type_check" CHECK (("type" = ANY (ARRAY['purchase'::"text", 'sale'::"text", 'transfer_out'::"text", 'transfer_in'::"text", 'adjustment'::"text"])))
);


ALTER TABLE "public"."transactions" OWNER TO "postgres";


COMMENT ON COLUMN "public"."transactions"."type" IS 'Transaction type: purchase (+), sale (-), transfer_out (-), transfer_in (+), adjustment (+/-)';



CREATE OR REPLACE VIEW "public"."inventory" AS
 SELECT "location_id",
    "product_id",
    "sum"(
        CASE
            WHEN ("type" = ANY (ARRAY['purchase'::"text", 'transfer_in'::"text"])) THEN "weight_kg"
            WHEN ("type" = ANY (ARRAY['sale'::"text", 'transfer_out'::"text"])) THEN (- "weight_kg")
            WHEN ("type" = 'adjustment'::"text") THEN "weight_kg"
            ELSE NULL::numeric
        END) AS "quantity_kg"
   FROM "public"."transactions" "t"
  WHERE ((("batch_id" IS NULL) OR (NOT (EXISTS ( SELECT 1
           FROM "public"."purchase_batches" "pb"
          WHERE (("pb"."id" = "t"."batch_id") AND ("pb"."is_voided" = true)))))) AND (("sale_batch_id" IS NULL) OR (NOT (EXISTS ( SELECT 1
           FROM "public"."sale_batches" "sb"
          WHERE (("sb"."id" = "t"."sale_batch_id") AND ("sb"."is_voided" = true)))))))
  GROUP BY "location_id", "product_id";


ALTER VIEW "public"."inventory" OWNER TO "postgres";


COMMENT ON VIEW "public"."inventory" IS 'Current stock levels, excludes voided batches';



CREATE TABLE IF NOT EXISTS "public"."locations" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "type" "text" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"(),
    CONSTRAINT "locations_type_check" CHECK (("type" = ANY (ARRAY['kiosk'::"text", 'mobile'::"text"])))
);


ALTER TABLE "public"."locations" OWNER TO "postgres";


CREATE TABLE IF NOT EXISTS "public"."products" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" "text" NOT NULL,
    "default_buy_price" numeric(10,2),
    "default_sell_price" numeric(10,2),
    "is_active" boolean DEFAULT true,
    "created_at" timestamp with time zone DEFAULT "now"(),
    "image_uri" "text",
    "local_id" "text" NOT NULL,
    "synced_at" timestamp with time zone,
    "server_updated_at" timestamp with time zone DEFAULT "now"()
);


ALTER TABLE "public"."products" OWNER TO "postgres";


COMMENT ON COLUMN "public"."products"."image_uri" IS 'Public URL for product image stored in Supabase Storage (nullable)';



CREATE OR REPLACE VIEW "public"."total_cash_balance" AS
 SELECT "sum"(
        CASE
            WHEN ("type" = 'deposit'::"text") THEN "amount"
            WHEN ("type" = ANY (ARRAY['withdrawal'::"text", 'payment'::"text", 'purchase'::"text"])) THEN (- "amount")
            ELSE NULL::numeric
        END) AS "balance"
   FROM "public"."cash_operations" "co"
  WHERE (("batch_id" IS NULL) OR (NOT (EXISTS ( SELECT 1
           FROM "public"."purchase_batches" "pb"
          WHERE (("pb"."id" = "co"."batch_id") AND ("pb"."is_voided" = true))))));


ALTER VIEW "public"."total_cash_balance" OWNER TO "postgres";


COMMENT ON VIEW "public"."total_cash_balance" IS 'Total cash balance across all locations, excludes voided purchase batches';



ALTER TABLE ONLY "public"."cash_operations"
    ADD CONSTRAINT "cash_operations_local_id_key" UNIQUE ("local_id");



ALTER TABLE ONLY "public"."cash_operations"
    ADD CONSTRAINT "cash_operations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."expense_categories"
    ADD CONSTRAINT "expense_categories_local_id_key" UNIQUE ("local_id");



ALTER TABLE ONLY "public"."expense_categories"
    ADD CONSTRAINT "expense_categories_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."locations"
    ADD CONSTRAINT "locations_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."products"
    ADD CONSTRAINT "products_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."purchase_batches"
    ADD CONSTRAINT "purchase_batches_local_id_key" UNIQUE ("local_id");



ALTER TABLE ONLY "public"."purchase_batches"
    ADD CONSTRAINT "purchase_batches_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."sale_batches"
    ADD CONSTRAINT "sale_batches_local_id_key" UNIQUE ("local_id");



ALTER TABLE ONLY "public"."sale_batches"
    ADD CONSTRAINT "sale_batches_pkey" PRIMARY KEY ("id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_local_id_key" UNIQUE ("local_id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_pkey" PRIMARY KEY ("id");



CREATE INDEX "idx_cash_operations_batch" ON "public"."cash_operations" USING "btree" ("batch_id");



CREATE INDEX "idx_cash_operations_category" ON "public"."cash_operations" USING "btree" ("category_id");



CREATE INDEX "idx_cash_operations_created" ON "public"."cash_operations" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_cash_operations_is_transfer" ON "public"."cash_operations" USING "btree" ("is_transfer") WHERE ("is_transfer" = true);



CREATE INDEX "idx_cash_operations_local_id" ON "public"."cash_operations" USING "btree" ("local_id");



CREATE INDEX "idx_cash_operations_location" ON "public"."cash_operations" USING "btree" ("location_id");



CREATE INDEX "idx_cash_operations_server_updated" ON "public"."cash_operations" USING "btree" ("server_updated_at");



CREATE INDEX "idx_cash_operations_synced" ON "public"."cash_operations" USING "btree" ("synced_at") WHERE ("synced_at" IS NULL);



CREATE INDEX "idx_cash_operations_type" ON "public"."cash_operations" USING "btree" ("type");



CREATE INDEX "idx_expense_categories_local_id" ON "public"."expense_categories" USING "btree" ("local_id");



CREATE INDEX "idx_expense_categories_server_updated" ON "public"."expense_categories" USING "btree" ("server_updated_at");



CREATE INDEX "idx_expense_categories_synced" ON "public"."expense_categories" USING "btree" ("synced_at") WHERE ("synced_at" IS NULL);



CREATE UNIQUE INDEX "idx_products_local_id" ON "public"."products" USING "btree" ("local_id");



CREATE INDEX "idx_products_server_updated" ON "public"."products" USING "btree" ("server_updated_at");



CREATE INDEX "idx_products_synced" ON "public"."products" USING "btree" ("synced_at") WHERE ("synced_at" IS NULL);



CREATE INDEX "idx_purchase_batches_corrects_batch_id" ON "public"."purchase_batches" USING "btree" ("corrects_batch_id");



CREATE INDEX "idx_purchase_batches_created" ON "public"."purchase_batches" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_purchase_batches_is_voided" ON "public"."purchase_batches" USING "btree" ("is_voided");



CREATE INDEX "idx_purchase_batches_local_id" ON "public"."purchase_batches" USING "btree" ("local_id");



CREATE INDEX "idx_purchase_batches_location" ON "public"."purchase_batches" USING "btree" ("location_id");



CREATE INDEX "idx_purchase_batches_server_updated" ON "public"."purchase_batches" USING "btree" ("server_updated_at");



CREATE INDEX "idx_purchase_batches_synced" ON "public"."purchase_batches" USING "btree" ("synced_at") WHERE ("synced_at" IS NULL);



CREATE INDEX "idx_sale_batches_corrects_batch_id" ON "public"."sale_batches" USING "btree" ("corrects_batch_id");



CREATE INDEX "idx_sale_batches_created" ON "public"."sale_batches" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_sale_batches_is_voided" ON "public"."sale_batches" USING "btree" ("is_voided");



CREATE INDEX "idx_sale_batches_local_id" ON "public"."sale_batches" USING "btree" ("local_id");



CREATE INDEX "idx_sale_batches_location" ON "public"."sale_batches" USING "btree" ("location_id");



CREATE INDEX "idx_sale_batches_server_updated" ON "public"."sale_batches" USING "btree" ("server_updated_at");



CREATE INDEX "idx_sale_batches_synced" ON "public"."sale_batches" USING "btree" ("synced_at") WHERE ("synced_at" IS NULL);



CREATE INDEX "idx_transactions_batch" ON "public"."transactions" USING "btree" ("batch_id");



CREATE INDEX "idx_transactions_created" ON "public"."transactions" USING "btree" ("created_at" DESC);



CREATE INDEX "idx_transactions_local_id" ON "public"."transactions" USING "btree" ("local_id");



CREATE INDEX "idx_transactions_location" ON "public"."transactions" USING "btree" ("location_id");



CREATE INDEX "idx_transactions_product" ON "public"."transactions" USING "btree" ("product_id");



CREATE INDEX "idx_transactions_sale_batch" ON "public"."transactions" USING "btree" ("sale_batch_id");



CREATE INDEX "idx_transactions_server_updated" ON "public"."transactions" USING "btree" ("server_updated_at");



CREATE INDEX "idx_transactions_synced" ON "public"."transactions" USING "btree" ("synced_at") WHERE ("synced_at" IS NULL);



CREATE INDEX "idx_transactions_type" ON "public"."transactions" USING "btree" ("type");



CREATE OR REPLACE TRIGGER "trg_cash_operations_server_updated_at" BEFORE INSERT OR UPDATE ON "public"."cash_operations" FOR EACH ROW EXECUTE FUNCTION "public"."update_server_updated_at"();



CREATE OR REPLACE TRIGGER "trg_expense_categories_server_updated_at" BEFORE INSERT OR UPDATE ON "public"."expense_categories" FOR EACH ROW EXECUTE FUNCTION "public"."update_server_updated_at"();



CREATE OR REPLACE TRIGGER "trg_products_server_updated_at" BEFORE INSERT OR UPDATE ON "public"."products" FOR EACH ROW EXECUTE FUNCTION "public"."update_server_updated_at"();



CREATE OR REPLACE TRIGGER "trg_purchase_batches_server_updated_at" BEFORE INSERT OR UPDATE ON "public"."purchase_batches" FOR EACH ROW EXECUTE FUNCTION "public"."update_server_updated_at"();



CREATE OR REPLACE TRIGGER "trg_sale_batches_server_updated_at" BEFORE INSERT OR UPDATE ON "public"."sale_batches" FOR EACH ROW EXECUTE FUNCTION "public"."update_server_updated_at"();



CREATE OR REPLACE TRIGGER "trg_transactions_server_updated_at" BEFORE INSERT OR UPDATE ON "public"."transactions" FOR EACH ROW EXECUTE FUNCTION "public"."update_server_updated_at"();



ALTER TABLE ONLY "public"."cash_operations"
    ADD CONSTRAINT "cash_operations_batch_id_fkey" FOREIGN KEY ("batch_id") REFERENCES "public"."purchase_batches"("id") ON DELETE RESTRICT;



ALTER TABLE ONLY "public"."cash_operations"
    ADD CONSTRAINT "cash_operations_category_id_fkey" FOREIGN KEY ("category_id") REFERENCES "public"."expense_categories"("id") ON DELETE SET NULL;



ALTER TABLE ONLY "public"."cash_operations"
    ADD CONSTRAINT "cash_operations_location_id_fkey" FOREIGN KEY ("location_id") REFERENCES "public"."locations"("id");



ALTER TABLE ONLY "public"."purchase_batches"
    ADD CONSTRAINT "purchase_batches_corrects_batch_id_fkey" FOREIGN KEY ("corrects_batch_id") REFERENCES "public"."purchase_batches"("id");



ALTER TABLE ONLY "public"."purchase_batches"
    ADD CONSTRAINT "purchase_batches_location_id_fkey" FOREIGN KEY ("location_id") REFERENCES "public"."locations"("id");



ALTER TABLE ONLY "public"."sale_batches"
    ADD CONSTRAINT "sale_batches_corrects_batch_id_fkey" FOREIGN KEY ("corrects_batch_id") REFERENCES "public"."sale_batches"("id");



ALTER TABLE ONLY "public"."sale_batches"
    ADD CONSTRAINT "sale_batches_location_id_fkey" FOREIGN KEY ("location_id") REFERENCES "public"."locations"("id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_batch_id_fkey" FOREIGN KEY ("batch_id") REFERENCES "public"."purchase_batches"("id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_location_id_fkey" FOREIGN KEY ("location_id") REFERENCES "public"."locations"("id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_product_id_fkey" FOREIGN KEY ("product_id") REFERENCES "public"."products"("id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_sale_batch_id_fkey" FOREIGN KEY ("sale_batch_id") REFERENCES "public"."sale_batches"("id");



ALTER TABLE ONLY "public"."transactions"
    ADD CONSTRAINT "transactions_transfer_location_id_fkey" FOREIGN KEY ("transfer_location_id") REFERENCES "public"."locations"("id");



CREATE POLICY "Allow all for anon" ON "public"."cash_operations" USING (true) WITH CHECK (true);



CREATE POLICY "Allow all for anon" ON "public"."expense_categories" USING (true) WITH CHECK (true);



CREATE POLICY "Allow all for anon" ON "public"."locations" USING (true) WITH CHECK (true);



CREATE POLICY "Allow all for anon" ON "public"."products" USING (true) WITH CHECK (true);



CREATE POLICY "Allow all for anon" ON "public"."purchase_batches" USING (true) WITH CHECK (true);



CREATE POLICY "Allow all for anon" ON "public"."sale_batches" USING (true) WITH CHECK (true);



CREATE POLICY "Allow all for anon" ON "public"."transactions" USING (true) WITH CHECK (true);



ALTER TABLE "public"."cash_operations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."expense_categories" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."locations" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."products" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."purchase_batches" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."sale_batches" ENABLE ROW LEVEL SECURITY;


ALTER TABLE "public"."transactions" ENABLE ROW LEVEL SECURITY;




ALTER PUBLICATION "supabase_realtime" OWNER TO "postgres";


GRANT USAGE ON SCHEMA "public" TO "postgres";
GRANT USAGE ON SCHEMA "public" TO "anon";
GRANT USAGE ON SCHEMA "public" TO "authenticated";
GRANT USAGE ON SCHEMA "public" TO "service_role";

























































































































































GRANT ALL ON FUNCTION "public"."update_server_updated_at"() TO "anon";
GRANT ALL ON FUNCTION "public"."update_server_updated_at"() TO "authenticated";
GRANT ALL ON FUNCTION "public"."update_server_updated_at"() TO "service_role";


















GRANT ALL ON TABLE "public"."cash_operations" TO "anon";
GRANT ALL ON TABLE "public"."cash_operations" TO "authenticated";
GRANT ALL ON TABLE "public"."cash_operations" TO "service_role";



GRANT ALL ON TABLE "public"."purchase_batches" TO "anon";
GRANT ALL ON TABLE "public"."purchase_batches" TO "authenticated";
GRANT ALL ON TABLE "public"."purchase_batches" TO "service_role";



GRANT ALL ON TABLE "public"."cash_balance" TO "anon";
GRANT ALL ON TABLE "public"."cash_balance" TO "authenticated";
GRANT ALL ON TABLE "public"."cash_balance" TO "service_role";



GRANT ALL ON TABLE "public"."expense_categories" TO "anon";
GRANT ALL ON TABLE "public"."expense_categories" TO "authenticated";
GRANT ALL ON TABLE "public"."expense_categories" TO "service_role";



GRANT ALL ON TABLE "public"."sale_batches" TO "anon";
GRANT ALL ON TABLE "public"."sale_batches" TO "authenticated";
GRANT ALL ON TABLE "public"."sale_batches" TO "service_role";



GRANT ALL ON TABLE "public"."transactions" TO "anon";
GRANT ALL ON TABLE "public"."transactions" TO "authenticated";
GRANT ALL ON TABLE "public"."transactions" TO "service_role";



GRANT ALL ON TABLE "public"."inventory" TO "anon";
GRANT ALL ON TABLE "public"."inventory" TO "authenticated";
GRANT ALL ON TABLE "public"."inventory" TO "service_role";



GRANT ALL ON TABLE "public"."locations" TO "anon";
GRANT ALL ON TABLE "public"."locations" TO "authenticated";
GRANT ALL ON TABLE "public"."locations" TO "service_role";



GRANT ALL ON TABLE "public"."products" TO "anon";
GRANT ALL ON TABLE "public"."products" TO "authenticated";
GRANT ALL ON TABLE "public"."products" TO "service_role";



GRANT ALL ON TABLE "public"."total_cash_balance" TO "anon";
GRANT ALL ON TABLE "public"."total_cash_balance" TO "authenticated";
GRANT ALL ON TABLE "public"."total_cash_balance" TO "service_role";









ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON SEQUENCES TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON FUNCTIONS TO "service_role";






ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "postgres";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "anon";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "authenticated";
ALTER DEFAULT PRIVILEGES FOR ROLE "postgres" IN SCHEMA "public" GRANT ALL ON TABLES TO "service_role";































