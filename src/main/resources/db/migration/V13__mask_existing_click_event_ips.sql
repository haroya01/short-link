-- GDPR/PIPA: 기존 row의 client_ip를 마스킹.
-- IPv4: 마지막 옥텟 → '*'  (e.g. 1.2.3.4 → 1.2.3.*)
-- IPv6: 첫 두 그룹만 유지   (e.g. 2001:db8:abcd:1234::1 → 2001:db8:*:*:*:*:*:*)
-- 비IP/형식 위반 row는 그대로 둠 (조작 위험 회피).

UPDATE click_event
SET client_ip = CONCAT(SUBSTRING_INDEX(client_ip, '.', 3), '.*')
WHERE client_ip LIKE '%.%.%.%' AND client_ip NOT LIKE '%.*';

UPDATE click_event
SET client_ip = CONCAT(
    SUBSTRING_INDEX(client_ip, ':', 2),
    ':*:*:*:*:*:*'
)
WHERE client_ip LIKE '%:%:%:%' AND client_ip NOT LIKE '%:\\*';
