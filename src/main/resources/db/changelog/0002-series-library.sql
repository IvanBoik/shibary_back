--liquibase formatted sql

-- Series library (admin-managed).
-- Text fields are bilingual (ru/en) except the episode title (English only) and the numeric
-- release year. Binary assets (image/video/subtitles) live in S3; only their object keys are
-- stored here. The number of seasons is derived from the `season` table, not stored on `series`.

--changeset boiko:0002-series-library
-- Drop any legacy series/season/episode tables created by the old pre-Liquibase schema.sql
-- (they used a different shape: release_years_ru/en and bilingual episode titles). The series
-- feature is new and holds no real data yet, so recreating from scratch is safe. On a fresh
-- database these DROPs are no-ops.
DROP TABLE IF EXISTS episode CASCADE;
DROP TABLE IF EXISTS season CASCADE;
DROP TABLE IF EXISTS series CASCADE;

CREATE TABLE series (
    id                UUID PRIMARY KEY,
    title_ru          TEXT NOT NULL,
    title_en          TEXT NOT NULL,
    genre_ru          TEXT NOT NULL,
    genre_en          TEXT NOT NULL,
    difficulty_ru     TEXT NOT NULL,
    difficulty_en     TEXT NOT NULL,
    accent_ru         TEXT NOT NULL,
    accent_en         TEXT NOT NULL,
    release_year      INT  NOT NULL,
    image_key         TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE season (
    id          UUID PRIMARY KEY,
    series_id   UUID NOT NULL REFERENCES series(id) ON DELETE CASCADE,
    number      INT  NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (series_id, number)
);

CREATE INDEX idx_season_series_id ON season (series_id);

CREATE TABLE episode (
    id                UUID PRIMARY KEY,
    season_id         UUID NOT NULL REFERENCES season(id) ON DELETE CASCADE,
    number            INT  NOT NULL,
    title             TEXT NOT NULL,
    video_key         TEXT NOT NULL,
    subtitles_ru_key  TEXT NOT NULL,
    subtitles_en_key  TEXT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (season_id, number)
);

CREATE INDEX idx_episode_season_id ON episode (season_id);
