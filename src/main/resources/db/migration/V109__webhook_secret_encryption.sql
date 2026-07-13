-- 웹훅 서명 시크릿을 평문 대신 AES-GCM(SecretCipher)으로 암호화 저장한다. 암호문(v1:base64...)이
-- 원본 48자보다 길어 컬럼을 넓힌다. 기존 평문 행은 prefix 없이 그대로 서명에 쓰여 호환된다.
ALTER TABLE link_webhook MODIFY COLUMN secret VARCHAR(255) NOT NULL;
ALTER TABLE blog_webhook MODIFY COLUMN secret VARCHAR(255) NOT NULL;
