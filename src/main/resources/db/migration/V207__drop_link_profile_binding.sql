-- Profile order and the featured flag live on link (profile_order, profile_highlighted). This
-- table was the unfinished copy from V61: nothing has read it, and since the release before this
-- one nothing writes it, so an image still running beside this migration never touches it.
DROP TABLE link_profile_binding;
