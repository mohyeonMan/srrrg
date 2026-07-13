-- 모든 링크가 Secure Redirect 흐름을 사용하므로 trusted 분기를 제거함.
ALTER TABLE links
DROP COLUMN trusted;
