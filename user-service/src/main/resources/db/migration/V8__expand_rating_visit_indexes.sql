ALTER TABLE customer_rating DROP CONSTRAINT IF EXISTS ck_customer_rating_visit_index;
ALTER TABLE customer_rating ADD CONSTRAINT ck_customer_rating_visit_index CHECK (visit_index >= 0);
