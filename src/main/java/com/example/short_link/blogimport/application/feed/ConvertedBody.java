package com.example.short_link.blogimport.application.feed;

import com.example.short_link.post.application.write.ReplacePostBlocksCommand.BlockInput;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ConvertedBody(List<Piece> pieces, String plainText) {

  public ConvertedBody {
    pieces = List.copyOf(pieces);
  }

  public record Piece(BlockInput block, Image image) {}

  public record Image(String url, String alt, String caption) {}

  public Set<String> imageUrls() {
    Set<String> urls = new LinkedHashSet<>();
    for (Piece piece : pieces) {
      if (piece.image() != null) {
        urls.add(piece.image().url());
      }
    }
    return urls;
  }

  public boolean isEmpty() {
    return pieces.isEmpty();
  }
}
