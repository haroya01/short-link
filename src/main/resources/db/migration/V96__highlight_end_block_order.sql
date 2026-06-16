-- 하이라이트가 여러 블록에 걸칠 수 있도록 end_block_order 를 추가한다. 하이라이트는
-- (block_order, start_offset) 에서 (end_block_order, end_offset) 까지 이어진다. 단일 블록
-- 하이라이트는 end_block_order == block_order (기존 모든 행이 이 경우). 옛 행 안전을 위해
-- nullable 로 두고, 코드는 NULL 을 block_order 와 같게 취급한다.
ALTER TABLE post_highlight ADD COLUMN end_block_order INT NULL AFTER block_order;
UPDATE post_highlight SET end_block_order = block_order WHERE end_block_order IS NULL;
