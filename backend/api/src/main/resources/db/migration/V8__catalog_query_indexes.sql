CREATE INDEX products_name_prefix_idx ON "${applicationSchema}".products(lower(name) text_pattern_ops);
CREATE INDEX products_sku_prefix_idx ON "${applicationSchema}".products(sku varchar_pattern_ops);
CREATE INDEX products_name_page_idx ON "${applicationSchema}".products(name,id);
CREATE INDEX products_status_page_idx ON "${applicationSchema}".products(status,name,id);
CREATE INDEX products_category_page_idx ON "${applicationSchema}".products(category_id,name,id);
CREATE INDEX categories_active_page_idx ON "${applicationSchema}".categories(active,name,id);
CREATE INDEX categories_name_page_idx ON "${applicationSchema}".categories(name,id);
