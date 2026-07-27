-- Privacy-relay traffic was stored as bots, which erased real readers from every "people" number.
--
-- iCloud Private Relay (and Cloudflare WARP) exit through Cloudflare (AS13335) and Fastly (AS54113).
-- Those two sat in the datacenter ASN list, so a genuine iPhone visitor was written as
-- is_bot = TRUE, bot_name = 'datacenter:Cloudflare, Inc.' — counted in the total but excluded from
-- people, unique visitors and every human-only breakdown. AsnResolver now treats those networks as
-- relays (people); this backfills the history so past numbers stop under-reporting.
--
-- Why matching on bot_name is precise: all three recorders reach the datacenter branch ONLY after
-- the crawler/prefetch label, the user-agent bot check and the burst heuristic have passed, so a
-- 'datacenter:%' name always means "browser-looking UA out of that network" — the relay case. Rows
-- flagged by the other paths carry a different name and are left alone. click_event and
-- profile_visit_event also store the ASN, so those get the tighter numeric filter; post_view_event
-- has no asn column, so it matches on the organisation embedded in the label.
UPDATE click_event
SET is_bot   = FALSE,
    bot_name = NULL
WHERE is_bot = TRUE
  AND asn IN (13335, 54113)
  AND bot_name LIKE 'datacenter:%';

UPDATE profile_visit_event
SET is_bot   = FALSE,
    bot_name = NULL
WHERE is_bot = TRUE
  AND asn IN (13335, 54113)
  AND bot_name LIKE 'datacenter:%';

UPDATE post_view_event
SET is_bot   = FALSE,
    bot_name = NULL
WHERE is_bot = TRUE
  AND (bot_name LIKE 'datacenter:Cloudflare%' OR bot_name LIKE 'datacenter:Fastly%');
