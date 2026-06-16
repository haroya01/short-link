-- 하이라이트에 공개 메모(큐레이터 귀속 마진 노트)를 옵션으로 단다. 모든 독자에게 함께 보인다.
ALTER TABLE post_highlight ADD COLUMN note VARCHAR(500) NULL AFTER quote;
