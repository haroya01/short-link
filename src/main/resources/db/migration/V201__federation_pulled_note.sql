-- A note kept because its server's newest public posts were pulled names that server in pulled_from;
-- a note a relay announced keeps it NULL. Both are the latest feed from elsewhere, and both are
-- deleted once untouched, as any relayed note.
ALTER TABLE federation_relayed_note
  ADD COLUMN pulled_from VARCHAR(253) NULL,
  ADD KEY idx_federation_relayed_note_pulled (pulled_from, note_id);
