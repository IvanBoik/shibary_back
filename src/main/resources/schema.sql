CREATE TABLE IF NOT EXISTS sentence (
    id         BIGSERIAL PRIMARY KEY,
    word       VARCHAR(255) NOT NULL,
    text       TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sentence_word ON sentence (word);
