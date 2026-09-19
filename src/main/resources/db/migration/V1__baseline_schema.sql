-- V1: Baseline schema for Quran backend (Slice B)
-- H2-compatible DDL. Flyway auto-runs this on Spring Boot startup.

-- ============================================================
-- 1. qc_chapter (114 surahs)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_chapter (
    id                  INT           NOT NULL PRIMARY KEY,
    revelation_place    VARCHAR(16)   NOT NULL,
    revelation_order    INT           NOT NULL,
    bismillah_pre       BOOLEAN       NOT NULL,
    name_simple         VARCHAR(64)   NOT NULL,
    name_complex        VARCHAR(64)   NOT NULL,
    name_arabic         VARCHAR(64)   NOT NULL,
    verses_count        INT           NOT NULL,
    page_start          INT           NOT NULL,
    page_end            INT           NOT NULL,
    translated_name     VARCHAR(128)  NOT NULL
);

-- ============================================================
-- 2. qc_verse (6236 ayat)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_verse (
    verse_key           VARCHAR(10)   NOT NULL PRIMARY KEY,
    chapter_id          INT           NOT NULL,
    verse_number        INT           NOT NULL,
    juz_number          INT           NOT NULL,
    hizb_number         INT           NOT NULL,
    rub_el_hizb_number  INT,
    ruku_number         INT           NOT NULL,
    manzil_number       INT           NOT NULL,
    page_number         INT           NOT NULL,
    sajdah_number       INT,
    text_uthmani        CLOB          NOT NULL,
    text_indopak        CLOB,
    text_imlaei         CLOB,
    CONSTRAINT fk_verse_chapter FOREIGN KEY (chapter_id) REFERENCES qc_chapter(id)
);

CREATE INDEX IF NOT EXISTS idx_verse_chapter_verse ON qc_verse (chapter_id, verse_number);
CREATE INDEX IF NOT EXISTS idx_verse_juz            ON qc_verse (juz_number);
CREATE INDEX IF NOT EXISTS idx_verse_hizb           ON qc_verse (hizb_number);
CREATE INDEX IF NOT EXISTS idx_verse_ruku           ON qc_verse (ruku_number);
CREATE INDEX IF NOT EXISTS idx_verse_manzil         ON qc_verse (manzil_number);
CREATE INDEX IF NOT EXISTS idx_verse_page           ON qc_verse (page_number);

-- ============================================================
-- 3. qc_word (~83,665 words)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_word (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    verse_key           VARCHAR(10)   NOT NULL,
    position            INT           NOT NULL,
    char_type_name      VARCHAR(16)   NOT NULL,
    text_uthmani        VARCHAR(64)   NOT NULL,
    text_indopak        VARCHAR(64),
    translation         VARCHAR(256),
    transliteration     VARCHAR(128),
    audio_relative_url  VARCHAR(64),
    page_number         INT           NOT NULL,
    line_number         INT           NOT NULL,
    CONSTRAINT fk_word_verse FOREIGN KEY (verse_key) REFERENCES qc_verse(verse_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_word_verse_position ON qc_word (verse_key, position);

-- ============================================================
-- 4. qc_translation (catalog)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_translation (
    id              INT          NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    author_name     VARCHAR(128) NOT NULL,
    slug            VARCHAR(64)  NOT NULL,
    language_name   VARCHAR(32)  NOT NULL
);

-- ============================================================
-- 5. qc_translation_verse (composite PK)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_translation_verse (
    translation_id  INT          NOT NULL,
    verse_key       VARCHAR(10)  NOT NULL,
    text            CLOB         NOT NULL,
    PRIMARY KEY (translation_id, verse_key),
    CONSTRAINT fk_tverse_translation FOREIGN KEY (translation_id) REFERENCES qc_translation(id),
    CONSTRAINT fk_tverse_verse       FOREIGN KEY (verse_key)      REFERENCES qc_verse(verse_key)
);

-- ============================================================
-- 6. qc_tafsir (catalog)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_tafsir (
    id              INT          NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    author_name     VARCHAR(128) NOT NULL,
    slug            VARCHAR(64)  NOT NULL,
    language_name   VARCHAR(32)  NOT NULL
);

-- ============================================================
-- 7. qc_tafsir_verse (composite PK)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_tafsir_verse (
    tafsir_id   INT          NOT NULL,
    verse_key   VARCHAR(10)  NOT NULL,
    text        CLOB         NOT NULL,
    PRIMARY KEY (tafsir_id, verse_key),
    CONSTRAINT fk_tafverse_tafsir FOREIGN KEY (tafsir_id) REFERENCES qc_tafsir(id),
    CONSTRAINT fk_tafverse_verse  FOREIGN KEY (verse_key) REFERENCES qc_verse(verse_key)
);

-- ============================================================
-- 8. qc_recitation (reciter catalog)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_recitation (
    id              INT          NOT NULL PRIMARY KEY,
    reciter_name    VARCHAR(128) NOT NULL,
    style           VARCHAR(32)
);

-- ============================================================
-- 9. qc_ayah_recitation (per-ayah audio + word timing segments)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_ayah_recitation (
    recitation_id   INT          NOT NULL,
    verse_key       VARCHAR(10)  NOT NULL,
    audio_url       VARCHAR(255) NOT NULL,
    segments        CLOB         NOT NULL,
    PRIMARY KEY (recitation_id, verse_key),
    CONSTRAINT fk_ayahrec_recitation FOREIGN KEY (recitation_id) REFERENCES qc_recitation(id),
    CONSTRAINT fk_ayahrec_verse      FOREIGN KEY (verse_key)     REFERENCES qc_verse(verse_key)
);

-- ============================================================
-- 10. qc_chapter_recitation (full-surah MP3s)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_chapter_recitation (
    recitation_id   INT          NOT NULL,
    chapter_id      INT          NOT NULL,
    audio_url       VARCHAR(255) NOT NULL,
    file_size       BIGINT       NOT NULL,
    PRIMARY KEY (recitation_id, chapter_id),
    CONSTRAINT fk_chaprec_recitation FOREIGN KEY (recitation_id) REFERENCES qc_recitation(id),
    CONSTRAINT fk_chaprec_chapter    FOREIGN KEY (chapter_id)    REFERENCES qc_chapter(id)
);

-- ============================================================
-- 11. qc_sync_state (per-resource sync status)
-- ============================================================
CREATE TABLE IF NOT EXISTS qc_sync_state (
    resource_filter   VARCHAR(128) NOT NULL PRIMARY KEY,
    sync_token        VARCHAR(255),
    status            VARCHAR(16)  NOT NULL,
    last_sync_at      TIMESTAMP,
    last_attempt_at   TIMESTAMP,
    error_message     CLOB,
    content_version   INT          NOT NULL DEFAULT 0
);
