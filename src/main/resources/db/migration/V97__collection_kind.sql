-- 컬렉션 종류 — COLLECTION(주제로 묶은 더미) | PATH(순서로 엮은 문장 논증 = reading path, A 척추).
-- 기존 컬렉션은 모두 COLLECTION. PATH 는 position 이 곧 논증의 순서라, 연결 재배치(reorder)로 흐름을 짠다.
ALTER TABLE collection
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'COLLECTION' AFTER visibility;
