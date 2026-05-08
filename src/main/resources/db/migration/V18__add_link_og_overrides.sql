ALTER TABLE link
  ADD COLUMN og_title_override VARCHAR(300) NULL,
  ADD COLUMN og_description_override VARCHAR(800) NULL,
  ADD COLUMN og_image_override VARCHAR(1024) NULL;
