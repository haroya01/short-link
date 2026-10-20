-- The default InnoDB stopword list holds 'a' and 'i', so the ngram parser left out every bigram
-- containing either: "Java" and "data" had no token in these indexes. An index takes the session's
-- stopword setting when it is built, so both are dropped and built again with stopwords off. A
-- single ALTER that drops and adds keeps the old setting, hence two statements per index. Flyway's
-- sessions keep the setting off (spring.flyway.init-sqls) so a later ALTER that rebuilds the table
-- does not bring the list back.
SET SESSION innodb_ft_enable_stopword = OFF;

ALTER TABLE note DROP INDEX ft_note_body;
ALTER TABLE note ADD FULLTEXT INDEX ft_note_body (body) WITH PARSER ngram;

ALTER TABLE post_search_text DROP INDEX ft_post_search_text;
ALTER TABLE post_search_text ADD FULLTEXT INDEX ft_post_search_text (search_text) WITH PARSER ngram;
