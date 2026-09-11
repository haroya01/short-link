package com.example.short_link.post.application.read;

import com.example.short_link.post.application.write.MarkdownBlocksConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 계정의 모든 글을 frontmatter와 본문을 가진 마크다운 ZIP으로 내보낸다. */
@Service
@RequiredArgsConstructor
public class PostExportQueryService {
  private final PostQueryService postQueryService;
  private final MarkdownBlocksConverter markdownBlocks;

  public byte[] export(Long userId) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
      for (PostView post : postQueryService.listMyPosts(userId)) {
        String markdown = markdownBlocks.toMarkdown(postQueryService.listBlocks(userId, post.id()));
        zip.putNextEntry(new ZipEntry(post.slug() + ".md"));
        zip.write(exportDocument(post, markdown).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return buffer.toByteArray();
  }

  /** 재발행에 필요한 최소 메타만 frontmatter 로 — 에디터의 .md 내보내기와 같은 문법. */
  private static String exportDocument(PostView post, String markdown) {
    StringBuilder head = new StringBuilder("---\n");
    head.append("title: \"").append(post.title().replace("\"", "\\\"")).append("\"\n");
    head.append("slug: ").append(post.slug()).append('\n');
    head.append("status: ").append(post.status()).append('\n');
    if (post.tags() != null && !post.tags().isEmpty()) {
      head.append("tags: [").append(String.join(", ", post.tags())).append("]\n");
    }
    if (post.publishedAt() != null) {
      head.append("published: ").append(post.publishedAt()).append('\n');
    }
    head.append("---\n\n");
    return head + markdown + "\n";
  }
}
