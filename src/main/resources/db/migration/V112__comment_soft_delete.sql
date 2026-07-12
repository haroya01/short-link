-- 댓글 소프트 삭제: 관리자 모더레이션(DELETE_COMMENT)이 감사/복구 여지를 남기고 공개 조회에서만 숨긴다.
-- deleted_at 이 채워진 댓글은 공개 목록(글별 댓글·내 댓글)에서 제외된다. 사용자 본인 삭제는 기존대로 물리 삭제.
ALTER TABLE comment ADD COLUMN deleted_at DATETIME(6) NULL;
