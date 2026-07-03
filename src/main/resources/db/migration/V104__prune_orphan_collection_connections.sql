-- collection_connection.ref_id 는 글·하이라이트·노트를 가리키는 다형 참조라 FK 가 없다(세 테이블 동시 FK 불가). 그
-- 대상이 하드 삭제돼도 DB 가 연결을 대신 지워주지 못해, 지금까지 삭제된 블록을 가리키는 고아 연결이 쌓였다 — 상세 화면은 조용히 건너뛰지만
-- countByCollectionId 는 죽은 행까지 세어 "표시 수 ≠ 렌더 수" 불일치를 만든다. 애플리케이션 삭제 경로에 정리를 넣기에 앞서, 이미 쌓인
-- 고아 행을 한 번 청소한다. id 는 NOT NULL PK 라 NOT IN 이 NULL 로 뒤틀리지 않는다.
DELETE FROM collection_connection
WHERE (block_type = 'POST' AND ref_id NOT IN (SELECT id FROM posts))
   OR (block_type = 'HIGHLIGHT' AND ref_id NOT IN (SELECT id FROM post_highlight))
   OR (block_type = 'NOTE' AND ref_id NOT IN (SELECT id FROM note));
