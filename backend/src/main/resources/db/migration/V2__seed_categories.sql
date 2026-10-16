-- The five MVP categories (CLAUDE.md, OQ-044 icon keys). Names may later be edited
-- through the content importer (upsert by slug); this seed never overwrites them.
INSERT INTO categories (slug, name_i18n, icon_key, sort_order, is_active) VALUES
    ('cografiya', '{"az": "Coğrafiya"}', 'geography', 10, TRUE),
    ('tarix',     '{"az": "Tarix"}',     'history',   20, TRUE),
    ('biologiya', '{"az": "Biologiya"}', 'biology',   30, TRUE),
    ('fizika',    '{"az": "Fizika"}',    'physics',   40, TRUE),
    ('idman',     '{"az": "İdman"}',     'sports',    50, TRUE)
ON CONFLICT (slug) DO NOTHING;
