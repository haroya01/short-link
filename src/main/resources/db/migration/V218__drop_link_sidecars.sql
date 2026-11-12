-- Since V217's release nothing reads or writes these tables, so a rollback to that image never
-- touches them.
DROP TABLE link_access_control;
DROP TABLE link_expiration_policy;
DROP TABLE link_og_metadata;
