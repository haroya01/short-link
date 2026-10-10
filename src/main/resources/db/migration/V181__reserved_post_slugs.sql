-- Profile page names (/p/{username}/notes, /about, …) shadow a post with the same slug on the web, so
-- such posts were never reachable there. Give each a free name: slug-2, or slug-{id} when slug-2 is
-- already that author's. No redirect: on the web the old address always opened the profile page.
UPDATE posts p
    LEFT JOIN posts taken ON taken.user_id = p.user_id AND taken.slug = CONCAT(p.slug, '-2')
SET p.slug = CASE WHEN taken.id IS NULL THEN CONCAT(p.slug, '-2') ELSE CONCAT(p.slug, '-', p.id) END
WHERE p.slug IN ('about', 'bookmarks', 'collections', 'feed', 'liked', 'media', 'notes',
                 'opengraph-image', 'replies', 'reposts', 'series');
