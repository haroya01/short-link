-- 컬렉션(Are.na 채널) — 글·하이라이트·노트를 주제로 잇는 큐레이션 단위. §0 "읽기의 연결 그래프".
-- connection = (컬렉션 × 블록) 다대다 조인. 같은 블록이 여러 컬렉션에 동시에 걸린다(멀티멤버십).
-- why = 큐레이터가 "왜 이었나" 한 줄(선택) — 단순 북마크와 컬렉션을 가르는 영혼.
-- 공개 화면엔 좋아요·팔로워 수 같은 허영 지표 컬럼이 없다(§0 바깥은 조용히).
CREATE TABLE collection (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id     BIGINT       NOT NULL,
    title        VARCHAR(120) NOT NULL,
    description  VARCHAR(280) NULL,
    visibility   VARCHAR(16)  NOT NULL,            -- PRIVATE | UNLISTED | PUBLIC
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    KEY idx_collection_owner (owner_id, updated_at),
    CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ref_id 는 다형 참조 — block_type 에 따라 post / post_highlight / note 의 PK 를 가리킨다.
-- 세 테이블을 동시에 FK 로 묶을 수 없어 의도적으로 FK 없음(애플리케이션이 대상 존재를 검증).
-- (collection_id, block_type, ref_id) 유니크 — 같은 블록을 같은 컬렉션에 두 번 못 잇는다(멱등).
CREATE TABLE collection_connection (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    collection_id  BIGINT       NOT NULL,
    block_type     VARCHAR(16)  NOT NULL,          -- POST | HIGHLIGHT | NOTE
    ref_id         BIGINT       NOT NULL,
    why            VARCHAR(280) NULL,
    position       INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_connection_block (collection_id, block_type, ref_id),
    KEY idx_connection_collection (collection_id, position),
    CONSTRAINT fk_connection_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
