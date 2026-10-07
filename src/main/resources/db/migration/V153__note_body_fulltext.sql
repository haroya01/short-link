ALTER TABLE note ADD FULLTEXT INDEX ft_note_body (body) WITH PARSER ngram;
