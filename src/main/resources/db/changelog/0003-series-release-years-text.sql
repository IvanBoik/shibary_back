--liquibase formatted sql

-- Change series.release_year (single integer) to release_years (free-form text), so the field can
-- hold a single year ("2009") or a range ("2009-2013"). The value has no language split (digits only).

--changeset boiko:0003-series-release-years-text
ALTER TABLE series ADD COLUMN release_years TEXT;
UPDATE series SET release_years = release_year::TEXT;
ALTER TABLE series ALTER COLUMN release_years SET NOT NULL;
ALTER TABLE series DROP COLUMN release_year;
