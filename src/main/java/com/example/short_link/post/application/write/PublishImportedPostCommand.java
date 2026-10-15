package com.example.short_link.post.application.write;

import java.time.Instant;
import java.util.List;

public record PublishImportedPostCommand(
    Long userId,
    Long postId,
    String title,
    String excerpt,
    String languageTag,
    List<String> tags,
    List<ReplacePostBlocksCommand.BlockInput> blocks,
    String coverUrl,
    String coverKey,
    boolean coverChosen,
    boolean excerptOnly,
    Long linkId,
    String shortCode,
    boolean hiddenFromDiscover,
    Instant originalPublishedAt) {

  public PublishImportedPostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (title == null || title.isBlank() || title.length() > 200) {
      throw new IllegalArgumentException("title required, max 200");
    }
    if (excerpt != null && excerpt.length() > 500) {
      throw new IllegalArgumentException("excerpt max 500");
    }
    PostMetadataValidation.requireLanguage(languageTag);
    tags = tags == null ? List.of() : List.copyOf(tags);
    blocks =
        new ReplacePostBlocksCommand(
                userId, postId, blocks == null ? List.of() : blocks, null, true)
            .blocks();
  }
}
