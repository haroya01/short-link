-- post_block.content 가 TEXT(최대 65,535 바이트)인데 본문 블록 가드는 블록당 100,000자
-- (utf8mb4 한글 기준 ~300KB)를 허용한다 — 긴 코드/문단 블록이 가드는 통과하고 컬럼에서 터져
-- 자동저장이 500 으로 죽었다(한글은 ~2.1만 자부터). 저장소를 가드와 같은 계약으로 넓힌다.
-- MEDIUMTEXT(16MB)는 블록당 100,000자 × 4바이트 최악까지 여유. 리비전 스냅샷(content_json)은
-- 이미 LONGTEXT 라 영향 없다.
ALTER TABLE post_block MODIFY content MEDIUMTEXT NULL;
