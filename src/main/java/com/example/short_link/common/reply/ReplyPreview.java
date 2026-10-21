package com.example.short_link.common.reply;

public record ReplyPreview(long parentId, long id, Author author, String body) {

  public record Author(long id, String username, String displayName, String avatarUrl) {}
}
