package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockContent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keep conversion compatible with the web editor's {@code markdown-to-blocks.ts}. CTA_REF has no
 * markdown authoring path; serialization preserves its payload verbatim.
 */
@Component
@RequiredArgsConstructor
public class MarkdownBlocksConverter {

  private final JsonMapper json;

  public List<ReplacePostBlocksCommand.BlockInput> toBlocks(String markdown) {
    if (markdown == null || markdown.isBlank()) return List.of();
    return new MarkdownBlockParser(json, convertCalloutContainers(markdown)).parse();
  }

  private static final Pattern FENCE_LINE = Pattern.compile("^\\s*(`{3,}|~{3,})(.*)$");

  static String convertCalloutContainers(String markdown) {
    if (!markdown.contains(":::")) return markdown;
    String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
    List<String> out = new ArrayList<>();
    String fence = null;
    for (int i = 0; i < lines.length; i++) {
      Matcher f = FENCE_LINE.matcher(lines[i]);
      if (f.matches()) {
        if (fence == null) fence = f.group(1);
        else if (f.group(1).startsWith(fence) && f.group(2).isBlank()) fence = null;
        out.add(lines[i]);
        continue;
      }
      String kind = fence == null ? calloutContainerKind(lines[i]) : null;
      int close = -1;
      if (kind != null) {
        for (int j = i + 1; j < lines.length; j++) {
          if (lines[j].trim().equals(":::")) {
            close = j;
            break;
          }
        }
      }
      if (close < 0) {
        out.add(lines[i]);
        continue;
      }
      out.add("> [!" + kind + "]");
      for (int j = i + 1; j < close; j++) {
        out.add(lines[j].isBlank() ? ">" : "> " + lines[j]);
      }
      i = close;
    }
    return String.join("\n", out);
  }

  private static String calloutContainerKind(String line) {
    return switch (line.trim().replaceAll("\\s+", " ").replace("::: ", ":::")) {
      case ":::note", ":::note info", ":::message" -> "NOTE";
      case ":::note warn" -> "WARNING";
      case ":::note alert", ":::message alert" -> "CAUTION";
      default -> null;
    };
  }

  public String toMarkdown(List<? extends PostBlockContent> blocks) {
    List<String> parts = new ArrayList<>();
    for (PostBlockContent block : blocks) {
      String part = serialize(block);
      if (part != null) parts.add(part);
    }
    return String.join("\n\n", parts);
  }

  private String serialize(PostBlockContent block) {
    String content = block.content();
    return switch (block.type()) {
      case "H1" -> "# " + nullToEmpty(content);
      case "H2" -> "## " + nullToEmpty(content);
      case "H3" -> "### " + nullToEmpty(content);
      case "QUOTE" -> quote(content);
      case "DIVIDER" -> "---";
      case "IMAGE" -> image(content);
      case "LIST_BULLET", "LIST_NUMBERED" -> list(content, block.type().equals("LIST_NUMBERED"));
      case "EMBED" -> embed(content);
      case "CODE" -> code(content);
      default -> content == null || content.isEmpty() ? null : content;
    };
  }

  private static String quote(String content) {
    StringBuilder result = new StringBuilder();
    for (String line : nullToEmpty(content).split("\n", -1)) {
      if (result.length() > 0) result.append('\n');
      result.append("> ").append(line);
    }
    return result.toString();
  }

  private String image(String content) {
    JsonNode node = readTreeOrNull(content);
    if (node == null || !node.path("url").isString()) return null;
    String alt = node.path("alt").isString() ? node.path("alt").stringValue() : "";
    String width = node.path("width").isString() ? node.path("width").stringValue() : null;
    String marked = width != null ? "«" + width + "» " + alt : alt;
    String caption = node.path("caption").isString() ? node.path("caption").stringValue() : "";
    String title =
        caption.isBlank()
            ? ""
            : " \"" + caption.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    return "![" + marked + "](" + node.path("url").stringValue() + title + ")";
  }

  private String list(String content, boolean numbered) {
    if (content == null || content.isEmpty()) return null;
    JsonNode node = readTreeOrNull(content);
    // 현재 형식은 중첩 가능한 마크다운, 구형 형식은 문자열 JSON 배열이다.
    if (node == null || !node.isArray()) return content;
    StringBuilder result = new StringBuilder();
    int number = 0;
    for (JsonNode item : node) {
      if (result.length() > 0) result.append('\n');
      result.append(numbered ? (++number) + ". " + item.asString() : "- " + item.asString());
    }
    return result.toString();
  }

  private String embed(String content) {
    if (content == null || content.isEmpty()) return null;
    JsonNode node = readTreeOrNull(content);
    return node != null && node.path("url").isString() ? node.path("url").stringValue() : content;
  }

  private String code(String content) {
    JsonNode node = readTreeOrNull(content);
    if (node == null) return null;
    String code = node.path("code").isString() ? node.path("code").stringValue() : "";
    String lang = node.path("lang").isString() ? node.path("lang").stringValue() : "";
    String fence = fenceFor(code);
    return fence + lang + "\n" + code + "\n" + fence;
  }

  /** A backtick fence longer than any run of backticks in the code, so the code can't break out. */
  static String fenceFor(String code) {
    int longest = 0;
    Matcher m = Pattern.compile("`+").matcher(code);
    while (m.find()) {
      longest = Math.max(longest, m.group().length());
    }
    return "`".repeat(Math.max(3, longest + 1));
  }

  private JsonNode readTreeOrNull(String content) {
    if (content == null || content.isEmpty()) return null;
    try {
      return json.readTree(content);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
