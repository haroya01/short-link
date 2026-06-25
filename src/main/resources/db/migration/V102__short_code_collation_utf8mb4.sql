-- V2 switched short_code to CHARACTER SET ascii / ascii_bin to make codes case-sensitive.
-- That left the column on a different charset from the utf8mb4 connection: the "my links"
-- search runs lower(short_code) LIKE :pattern, where :pattern binds as utf8mb4_0900_ai_ci.
-- MySQL cannot reconcile an ascii_bin column expression with a utf8mb4 literal and throws
-- "Illegal mix of collations (ascii_bin), (utf8mb4_0900_ai_ci) for operation 'like'".
--
-- utf8mb4_0900_bin keeps short codes case-sensitive (binary comparison, so the unique key and
-- redirect lookups stay exact) while sharing the utf8mb4 charset, so the comparison resolves.
-- Short codes are ASCII-only ([0-9A-Za-z], length 7), so no stored value changes.
ALTER TABLE link
    MODIFY COLUMN short_code VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL;
