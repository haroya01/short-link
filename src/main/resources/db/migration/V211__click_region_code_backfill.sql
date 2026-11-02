-- Japanese and Korean clicks saved before V210 get region_code from region_name. The pairs are every
-- subdivision GeoLite2-City holds for JP and KR, the same in its 2026-05-01 and 2026-10-30 builds,
-- which span every region_name stored so far. A name outside them stays NULL. No subdivision of
-- another country has one of these names, even ignoring case and accents as utf8mb4_unicode_ci
-- does, so the rollup rows, which keep no country, are matched on the name alone.
CREATE TEMPORARY TABLE region_code_backfill (
  country CHAR(2)      NOT NULL,
  name    VARCHAR(128) NOT NULL,
  code    VARCHAR(6)   NOT NULL,
  PRIMARY KEY (country, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO region_code_backfill (country, name, code) VALUES
  ('JP', 'Hokkaido', 'JP-01'),
  ('JP', 'Aomori', 'JP-02'),
  ('JP', 'Iwate', 'JP-03'),
  ('JP', 'Miyagi', 'JP-04'),
  ('JP', 'Akita', 'JP-05'),
  ('JP', 'Yamagata', 'JP-06'),
  ('JP', 'Fukushima', 'JP-07'),
  ('JP', 'Ibaraki', 'JP-08'),
  ('JP', 'Tochigi', 'JP-09'),
  ('JP', 'Gunma', 'JP-10'),
  ('JP', 'Saitama', 'JP-11'),
  ('JP', 'Chiba', 'JP-12'),
  ('JP', 'Tokyo', 'JP-13'),
  ('JP', 'Kanagawa', 'JP-14'),
  ('JP', 'Niigata', 'JP-15'),
  ('JP', 'Toyama', 'JP-16'),
  ('JP', 'Ishikawa', 'JP-17'),
  ('JP', 'Fukui', 'JP-18'),
  ('JP', 'Yamanashi', 'JP-19'),
  ('JP', 'Nagano', 'JP-20'),
  ('JP', 'Gifu', 'JP-21'),
  ('JP', 'Shizuoka', 'JP-22'),
  ('JP', 'Aichi', 'JP-23'),
  ('JP', 'Mie', 'JP-24'),
  ('JP', 'Shiga', 'JP-25'),
  ('JP', 'Kyoto', 'JP-26'),
  ('JP', 'Osaka', 'JP-27'),
  ('JP', 'Hyōgo', 'JP-28'),
  ('JP', 'Nara', 'JP-29'),
  ('JP', 'Wakayama', 'JP-30'),
  ('JP', 'Tottori', 'JP-31'),
  ('JP', 'Shimane', 'JP-32'),
  ('JP', 'Okayama', 'JP-33'),
  ('JP', 'Hiroshima', 'JP-34'),
  ('JP', 'Yamaguchi', 'JP-35'),
  ('JP', 'Tokushima', 'JP-36'),
  ('JP', 'Kagawa', 'JP-37'),
  ('JP', 'Ehime', 'JP-38'),
  ('JP', 'Kochi', 'JP-39'),
  ('JP', 'Fukuoka', 'JP-40'),
  ('JP', 'Saga', 'JP-41'),
  ('JP', 'Nagasaki', 'JP-42'),
  ('JP', 'Kumamoto', 'JP-43'),
  ('JP', 'Oita', 'JP-44'),
  ('JP', 'Miyazaki', 'JP-45'),
  ('JP', 'Kagoshima', 'JP-46'),
  ('JP', 'Okinawa', 'JP-47'),
  ('KR', 'Seoul', 'KR-11'),
  ('KR', 'Busan', 'KR-26'),
  ('KR', 'Daegu', 'KR-27'),
  ('KR', 'Incheon', 'KR-28'),
  ('KR', 'Gwangju', 'KR-29'),
  ('KR', 'Daejeon', 'KR-30'),
  ('KR', 'Ulsan', 'KR-31'),
  ('KR', 'Gyeonggi-do', 'KR-41'),
  ('KR', 'Gangwon-do', 'KR-42'),
  ('KR', 'North Chungcheong', 'KR-43'),
  ('KR', 'Chungcheongnam-do', 'KR-44'),
  ('KR', 'Jeollabuk-do', 'KR-45'),
  ('KR', 'Jeollanam-do', 'KR-46'),
  ('KR', 'Gyeongsangbuk-do', 'KR-47'),
  ('KR', 'Gyeongsangnam-do', 'KR-48'),
  ('KR', 'Jeju-do', 'KR-49'),
  ('KR', 'Sejong-si', 'KR-50');

UPDATE click_event c
    JOIN region_code_backfill m ON m.country = c.country_code AND m.name = c.region_name
SET c.region_code = m.code
WHERE c.region_code IS NULL;

UPDATE click_daily_dim d
    JOIN region_code_backfill m ON m.name = d.value
SET d.region_code = m.code
WHERE d.dim = 18 AND d.region_code IS NULL;

DROP TEMPORARY TABLE region_code_backfill;
