-- Hot-path query indexes (DB is the binding constraint). Secondary-index ADD/DROP on MySQL 8 is
-- online (INPLACE), so these don't block reads/writes; on a large click_event the ADD still takes
-- time to build — expected during deploy.

-- 1) click_event analytics: nearly every read is `WHERE link_id=? AND is_bot=0 [AND clicked_at>=?]`.
--    The existing idx_click_event_link_id_clicked_at(link_id, clicked_at) lacks is_bot, so the
--    human/bot split filters on the heap after the range scan. Lead with (link_id, is_bot) so the
--    bot filter + time window are index-resolved.
ALTER TABLE click_event ADD KEY idx_click_event_link_bot_time (link_id, is_bot, clicked_at);

-- 2) public feed: `WHERE status='PUBLISHED' ORDER BY published_at DESC`. Only per-user indexes
--    exist (none lead with status), so the global feed filesorts every published post.
ALTER TABLE posts ADD KEY idx_posts_status_published (status, published_at);

-- 3) per-post / per-author click attribution: `post_id=? AND clicked_at>=?`. The V73 single-column
--    idx_click_event_post(post_id) is fully covered by this composite's leftmost prefix, so replace it.
ALTER TABLE click_event ADD KEY idx_click_event_post_time (post_id, clicked_at);
DROP INDEX idx_click_event_post ON click_event;

-- 4) click_event is write-heavy (one INSERT per redirect); every secondary index is maintained on
--    insert. These two back only narrow, low-traffic admin reads (top source-channel alert, ASN
--    grouping) — drop them to cut per-insert maintenance. The (link_id, ...) reads they served fall
--    back to idx_click_event_link_bot_time's leading column.
DROP INDEX idx_click_event_source_channel ON click_event;
DROP INDEX idx_click_event_asn ON click_event;
