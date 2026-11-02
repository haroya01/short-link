-- region_code is the ISO 3166-2 code of the click's most specific subdivision, the country code and
-- the subdivision code joined by a hyphen (JP-13, KR-11, GB-WBK), read from the same GeoLite2
-- record as region_name, which keeps the English name. NULL when GeoLite2 gives no subdivision code
-- and on rows written before this release that V211 does not fill. click_daily_dim sets it only on
-- dim 18 (REGION) rows, as the largest code among the day's clicks with that name, so a read that
-- groups by name keeps one row per name. The release before this one never reads either column.
ALTER TABLE click_event ADD COLUMN region_code VARCHAR(6) NULL;

ALTER TABLE click_daily_dim ADD COLUMN region_code VARCHAR(6) NULL;
