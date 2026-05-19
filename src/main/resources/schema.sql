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

