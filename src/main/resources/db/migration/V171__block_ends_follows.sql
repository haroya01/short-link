DELETE f FROM user_follow f
JOIN user_block b
  ON (b.blocker_id = f.follower_id AND b.blocked_id = f.following_id)
  OR (b.blocker_id = f.following_id AND b.blocked_id = f.follower_id);

DELETE r FROM follow_request r
JOIN user_block b
  ON (b.blocker_id = r.follower_id AND b.blocked_id = r.following_id)
  OR (b.blocker_id = r.following_id AND b.blocked_id = r.follower_id);

DELETE n FROM notification n
JOIN user_block b
  ON (b.blocker_id = n.recipient_user_id AND b.blocked_id = n.actor_user_id)
  OR (b.blocker_id = n.actor_user_id AND b.blocked_id = n.recipient_user_id)
WHERE n.type = 'FOLLOW_REQUEST';
