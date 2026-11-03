-- The link report's visitor reads (unique visitors, return rate, channel depth) filter on
-- (link_id, is_bot) and read visitor_hash, clicked_at and referrer_host. This index holds all of them,
-- so those reads stay inside one link's index range instead of fetching every click row. It keeps
-- (link_id) as its prefix and serves the (link_id, is_bot = 0, visitor_hash) lookups that
-- idx_click_event_visitor served. Secondary index ADD/DROP is online (INPLACE, no lock); the build
-- takes time on a large click_event during deploy.
ALTER TABLE click_event
  DROP INDEX idx_click_event_visitor,
  ADD INDEX idx_click_event_link_bot_visitor (link_id, is_bot, visitor_hash, clicked_at, referrer_host),
  ALGORITHM = INPLACE, LOCK = NONE;
