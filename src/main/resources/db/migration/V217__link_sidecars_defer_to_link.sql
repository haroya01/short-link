-- The previous image reads COALESCE(sidecar, link); emptied sidecar columns make it read the link row.
UPDATE link_access_control
SET password_hash = NULL,
    max_views = NULL;

UPDATE link_expiration_policy
SET blocked_countries = NULL,
    expired_message = NULL,
    expired_redirect_url = NULL;

UPDATE link_og_metadata
SET og_title = NULL,
    og_description = NULL,
    og_image = NULL,
    og_fetched_at = NULL,
    og_title_override = NULL,
    og_description_override = NULL,
    og_image_override = NULL;
