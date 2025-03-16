-- Tables

CREATE TABLE media_library_entries (
unique_id TEXT PRIMARY KEY,
title TEXT NOT NULL,
origin_url TEXT NOT NULL,
hits INTEGER NOT NULL DEFAULT 0,
marked_for_deletion INTEGER NOT NULL DEFAULT 0,
creation_date INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE media_library_tombstones(
unique_id TEXT PRIMARY KEY
);

CREATE TABLE tags(
id INTEGER PRIMARY KEY,
name TEXT NOT NULL UNIQUE
);

CREATE TABLE tags_for_library_entries (
library_entry_uuid TEXT NOT NULL REFERENCES media_library_entries ON DELETE CASCADE,
tag_id INTEGER NOT NULL REFERENCES tags(id)
);

CREATE TABLE duplicates (
src_id TEXT NOT NULL,
dup_id TEXT NOT NULL,
distance INTEGER NOT NULL
);

CREATE TABLE events (
id INTEGER PRIMARY KEY,
event STRING,
TIMESTAMP DATETIME DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW'))
);

-- Indexes

CREATE UNIQUE INDEX unq_uuid_tag_id
ON tags_for_library_entries(library_entry_uuid, tag_id);

CREATE INDEX idx_duplicates_src_id
ON duplicates(src_id);

CREATE UNIQUE INDEX unq_src_dup_ids
ON duplicates(src_id, dup_id);
