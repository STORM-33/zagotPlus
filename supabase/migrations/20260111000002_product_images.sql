-- Add image_uri column to products table
-- Stores content:// URI or file path for product images
ALTER TABLE products ADD COLUMN image_uri TEXT;

-- Add comment for documentation
COMMENT ON COLUMN products.image_uri IS 'URI or file path for product image (nullable)';
