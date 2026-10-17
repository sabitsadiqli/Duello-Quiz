-- Image pipeline (docs/mvp-spec.md §5). Files stay on disk (ImageStorage); the row keeps
-- metadata only. New columns are nullable so the migration applies to any existing data.
--   thumb_*        size of the thumbnail file
--   quality        WebP quality actually used (lowered stepwise when above the size target)
--   source_sha256  SHA-256 of the original upload: the same source is processed and stored
--                  once (idempotent imports and uploads)
--   source_url     where the original comes from (licence attribution)
--   alt_i18n       default alt text; a question's own image_alt_i18n wins
ALTER TABLE images
    ADD COLUMN thumb_width    INT,
    ADD COLUMN thumb_height   INT,
    ADD COLUMN thumb_bytes    INT,
    ADD COLUMN quality        SMALLINT,
    ADD COLUMN thumb_quality  SMALLINT,
    ADD COLUMN source_sha256  CHAR(64),
    ADD COLUMN source_url     TEXT,
    ADD COLUMN alt_i18n       JSONB,
    ADD CONSTRAINT images_thumb_dimensions_positive CHECK (
        (thumb_width IS NULL OR thumb_width > 0) AND (thumb_height IS NULL OR thumb_height > 0)
        AND (thumb_bytes IS NULL OR thumb_bytes > 0)
    ),
    ADD CONSTRAINT images_quality_range CHECK (
        (quality IS NULL OR quality BETWEEN 1 AND 100) AND (thumb_quality IS NULL OR thumb_quality BETWEEN 1 AND 100)
    ),
    ADD CONSTRAINT images_source_sha256_format CHECK (source_sha256 IS NULL OR source_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT images_alt_has_az CHECK (alt_i18n IS NULL OR (jsonb_typeof(alt_i18n) = 'object' AND alt_i18n ? 'az'));

CREATE UNIQUE INDEX images_source_sha256_key ON images (source_sha256) WHERE source_sha256 IS NOT NULL;
