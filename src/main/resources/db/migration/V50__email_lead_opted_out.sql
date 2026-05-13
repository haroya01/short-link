-- Owner-side opt-out flag on each lead. Opted-out rows stay in the dashboard so the owner can
-- still see who unsubscribed and re-include them if needed, but are excluded from CSV export
-- (and any future campaign send) by default.
ALTER TABLE email_lead
    ADD COLUMN opted_out BOOLEAN NOT NULL DEFAULT FALSE;
