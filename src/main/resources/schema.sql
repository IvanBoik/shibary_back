CREATE TABLE IF NOT EXISTS sentence (
    id         BIGSERIAL PRIMARY KEY,
    word       VARCHAR(255) NOT NULL,
    word_ru    VARCHAR(255) NOT NULL,
    text       TEXT         NOT NULL,
    text_ru    TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sentence_word ON sentence (word);

DELETE FROM sentence s
WHERE s.id NOT IN (
    SELECT MIN(id)
    FROM sentence
    GROUP BY word, text
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sentence_word_text ON sentence (word, text);

CREATE TABLE IF NOT EXISTS word_info (
    id         BIGSERIAL PRIMARY KEY,
    word       VARCHAR(255) NOT NULL UNIQUE,
    definition TEXT         NOT NULL,
    synonyms   TEXT         NOT NULL,
    antonyms   TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_word_info_word ON word_info (word);

CREATE TABLE IF NOT EXISTS users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(320) UNIQUE,
    password_hash  TEXT,
    display_name   TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (LOWER(email)) WHERE email IS NOT NULL;

CREATE TABLE IF NOT EXISTS oauth_accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE IF NOT EXISTS user_sync_state (
    user_id          UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    current_revision BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_words (
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    uuid            TEXT NOT NULL,
    english         TEXT NOT NULL,
    russian         TEXT NOT NULL,
    repeat_days     INT NOT NULL,
    added_date      TEXT NOT NULL,
    is_learned      BOOLEAN NOT NULL,
    stage           INT NOT NULL,
    next_review_at  TEXT NOT NULL,
    last_review_at  TEXT NOT NULL,
    correct_streak  INT NOT NULL,
    is_deleted      BOOLEAN NOT NULL,
    updated_at      BIGINT NOT NULL,
    server_revision BIGINT NOT NULL,
    PRIMARY KEY (user_id, uuid)
);

CREATE INDEX IF NOT EXISTS idx_user_words_revision ON user_words (user_id, server_revision);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id                UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    language               TEXT,
    theme                  TEXT,
    enabled_question_types TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    repetition_mode        TEXT,
    daily_review_limit     INT,
    new_words_per_day      INT,
    reminders_enabled      BOOLEAN,
    reminder_hour          INT,
    reminder_minute        INT,
    updated_at             BIGINT NOT NULL,
    server_revision        BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_settings_revision ON user_settings (user_id, server_revision);

CREATE TABLE IF NOT EXISTS user_game_scores (
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pair_count        INT NOT NULL,
    best_time_millis  BIGINT NOT NULL,
    updated_at        BIGINT NOT NULL,
    server_revision   BIGINT NOT NULL,
    PRIMARY KEY (user_id, pair_count)
);

CREATE INDEX IF NOT EXISTS idx_user_game_scores_revision ON user_game_scores (user_id, server_revision);

