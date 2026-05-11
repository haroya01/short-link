-- PRODUCT_CARD block JSON (multiple items with image URLs + price + description + CTA URL) needs
-- more headroom than VARCHAR(2048). Switching to TEXT (~64KB) is non-destructive — every existing
-- VARCHAR row fits trivially, and Hibernate's columnDefinition on the entity already declares TEXT
-- so DDL stays aligned across environments.
ALTER TABLE profile_block MODIFY COLUMN content TEXT NULL;
