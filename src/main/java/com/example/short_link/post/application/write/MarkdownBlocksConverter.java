package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.domain.PostBlockType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Markdown ↔ block-model conversion, ported line-for-line from the web editor's {@code
 * markdown-to-blocks.ts} so the native app and the web produce identical block streams for the same
 * markdown. 13 block types round-trip except {@code CTA_REF}, which has no markdown authoring path
 * and is preserved verbatim. The frontend copy remains the reference — when the mapping changes
 * there, mirror it here (and vice versa).
 */
@Component
@RequiredArgsConstructor
public class MarkdownBlocksConverter {

  private final JsonMapper json;

  private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})(.*)$");
  private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
  private static final Pattern QUOTE = Pattern.compile("^>\\s*(.*)$");
  // 표준 마크다운 image title `![alt](url "캡션")` 의 title 을 캡션으로 분리 캡처(group 3).
  private static final Pattern IMAGE =
      Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\)");
  private static final Pattern AUTOLINK = Pattern.compile("^<(https?://[^>\\s]+)>$");
  private static final Pattern LINK_ONLY =
      Pattern.compile("^\\[[^\\]]*\\]\\((https?://[^)\\s]+)\\)$");
  private static final Pattern BARE_URL = Pattern.compile("^(https?://\\S+)$");
  private static final Pattern LIST_START = Pattern.compile("^(?:[-*]|\\d+\\.)\\s+.*");
  private static final Pattern LIST_CONT = Pattern.compile("^\\s*(?:[-*]|\\d+\\.)\\s+.*");
  private static final Pattern INDENTED = Pattern.compile("^\\s+\\S.*");
  private static final Pattern TABLE_SEP = Pattern.compile("^[\\s|:-]+$");
  private static final Pattern PARA_BREAK =
      Pattern.compile("^(#{1,3}\\s|>\\s|!\\[|[-*]\\s|\\d+\\.\\s).*");

  // Medium-style per-image width, carried as an alt-text marker prefix (the only metadata that
  // survives the markdown round-trip). Stripped before storage, re-attached on serialize.
  private static final String[] WIDTHS = {"wide", "full", "half"};

  // MARK: markdown → blocks

  public List<ReplacePostBlocksCommand.BlockInput> toBlocks(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return List.of();
    }
    String[] lines = markdown.split("\n", -1);
    List<ReplacePostBlocksCommand.BlockInput> blocks = new ArrayList<>();
    int i = 0;

    while (i < lines.length) {
      String line = lines[i];

      if (line.trim().isEmpty()) {
        i++;
        continue;
      }

      // Fenced code block — consume the whole region (incl. blank / markdown-like lines) so the
      // line-based rules below can't tear it apart.
      Matcher fence = FENCE.matcher(line);
      if (fence.matches()) {
        String marker = fence.group(1).substring(0, 1).repeat(3);
        String langPart = fence.group(2).trim();
        String lang = langPart.isEmpty() ? null : langPart.split("\\s+")[0];
        List<String> code = new ArrayList<>();
        i++;
        while (i < lines.length) {
          if (lines[i].stripLeading().startsWith(marker)) {
            i++;
            break;
          }
          code.add(lines[i]);
          i++;
        }
        ObjectNode node = json.createObjectNode();
        if (lang == null) {
          node.putNull("lang");
        } else {
          node.put("lang", lang);
        }
        node.put("code", String.join("\n", code));
        blocks.add(block(PostBlockType.CODE, json.writeValueAsString(node)));
        continue;
      }

      // GFM table (header row + "| --- |" separator + body rows) → raw markdown in one block.
      if (isTableStart(line, i + 1 < lines.length ? lines[i + 1] : null)) {
        List<String> rows = new ArrayList<>();
        while (i < lines.length && lines[i].stripLeading().startsWith("|")) {
          rows.add(lines[i]);
          i++;
        }
        blocks.add(block(PostBlockType.TABLE, String.join("\n", rows)));
        continue;
      }

      if (line.trim().equals("---")) {
        blocks.add(block(PostBlockType.DIVIDER, null));
        i++;
        continue;
      }

      Matcher heading = HEADING.matcher(line);
      if (heading.matches()) {
        PostBlockType type =
            switch (heading.group(1).length()) {
              case 1 -> PostBlockType.H1;
              case 2 -> PostBlockType.H2;
              default -> PostBlockType.H3;
            };
        blocks.add(block(type, heading.group(2).trim()));
        i++;
        continue;
      }

      Matcher quote = QUOTE.matcher(line);
      if (quote.matches()) {
        // Coalesce consecutive `>` lines into ONE quote — one block per line rendered as N
        // adjacent quote boxes.
        StringBuilder quoteLines = new StringBuilder(quote.group(1));
        i++;
        while (i < lines.length) {
          Matcher qm = QUOTE.matcher(lines[i]);
          if (!qm.matches()) break;
          quoteLines.append('\n').append(qm.group(1));
          i++;
        }
        blocks.add(block(PostBlockType.QUOTE, quoteLines.toString().trim()));
        continue;
      }

      // One OR MORE images on a line (a side-by-side «half» pair serializes adjacent) → one IMAGE
      // block each. Only when the line is *nothing but* images.
      Matcher img = IMAGE.matcher(line);
      List<String[]> images = new ArrayList<>();
      while (img.find()) {
        images.add(new String[] {img.group(1), img.group(2), img.group(3)});
      }
      if (!images.isEmpty() && IMAGE.matcher(line).replaceAll("").trim().isEmpty()) {
        for (String[] im : images) {
          ObjectNode node = json.createObjectNode();
          node.put("url", im[1]);
          String alt = im[0];
          String width = null;
          for (String w : WIDTHS) {
            String mark = "«" + w + "» ";
            if (alt.startsWith(mark)) {
              width = w;
              alt = alt.substring(mark.length());
              break;
            }
          }
          node.put("alt", alt);
          if (width != null) {
            node.put("width", width);
          }
          if (im[2] != null && !im[2].isBlank()) {
            node.put("caption", im[2].trim());
          }
          blocks.add(block(PostBlockType.IMAGE, json.writeValueAsString(node)));
        }
        i++;
        continue;
      }

      String embedUrl = standaloneEmbedUrl(line);
      if (embedUrl != null) {
        blocks.add(block(PostBlockType.EMBED, embedUrl));
        i++;
        continue;
      }

      // A markdown list (bullet or numbered), possibly NESTED — capture the whole region as raw
      // markdown so nesting round-trips. Block type follows the first line.
      if (LIST_START.matcher(line).matches()) {
        boolean ordered = Character.isDigit(line.charAt(0));
        List<String> listLines = new ArrayList<>();
        while (i < lines.length
            && !lines[i].trim().isEmpty()
            && (LIST_CONT.matcher(lines[i]).matches() || INDENTED.matcher(lines[i]).matches())) {
          listLines.add(lines[i]);
          i++;
        }
        blocks.add(
            block(
                ordered ? PostBlockType.LIST_NUMBERED : PostBlockType.LIST_BULLET,
                String.join("\n", listLines)));
        continue;
      }

      // PARAGRAPH — consecutive non-empty lines. Always consume the current line FIRST so `i`
      // advances even when the line matched no rule above (e.g. an image with a trailing caption);
      // otherwise the loop would spin forever.
      List<String> paraLines = new ArrayList<>();
      paraLines.add(lines[i]);
      i++;
      while (i < lines.length
          && !lines[i].trim().isEmpty()
          && !lines[i].trim().equals("---")
          && !lines[i].startsWith("```")
          && !lines[i].startsWith("~~~")
          && !isTableStart(lines[i], i + 1 < lines.length ? lines[i + 1] : null)
          && !PARA_BREAK.matcher(lines[i]).matches()
          && standaloneEmbedUrl(lines[i]) == null) {
        paraLines.add(lines[i]);
        i++;
      }
      blocks.add(block(PostBlockType.PARAGRAPH, String.join("\n", paraLines)));
    }

    return blocks;
  }

  // MARK: blocks → markdown

  public String toMarkdown(List<PostBlockView> blocks) {
    List<String> parts = new ArrayList<>();
    for (PostBlockView b : blocks) {
      String content = b.content();
      switch (b.type()) {
        case "H1" -> parts.add("# " + nullToEmpty(content));
        case "H2" -> parts.add("## " + nullToEmpty(content));
        case "H3" -> parts.add("### " + nullToEmpty(content));
        case "QUOTE" -> {
          // Prefix every line so a multi-line quote round-trips back to ONE QUOTE block.
          StringBuilder sb = new StringBuilder();
          for (String l : nullToEmpty(content).split("\n", -1)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("> ").append(l);
          }
          parts.add(sb.toString());
        }
        case "DIVIDER" -> parts.add("---");
        case "IMAGE" -> {
          JsonNode node = readTreeOrNull(content);
          if (node != null && node.path("url").isString()) {
            String alt = node.path("alt").isString() ? node.path("alt").stringValue() : "";
            String width = node.path("width").isString() ? node.path("width").stringValue() : null;
            String marked = width != null ? "«" + width + "» " + alt : alt;
            String caption =
                node.path("caption").isString() ? node.path("caption").stringValue() : "";
            String title = caption.isBlank() ? "" : " \"" + caption.replace("\"", "'") + "\"";
            parts.add("![" + marked + "](" + node.path("url").stringValue() + title + ")");
          }
        }
        case "LIST_BULLET", "LIST_NUMBERED" -> {
          if (content == null || content.isEmpty()) break;
          // New format = raw markdown (nesting-capable). Legacy = JSON array of flat strings.
          JsonNode node = readTreeOrNull(content);
          if (node != null && node.isArray()) {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (JsonNode item : node) {
              if (sb.length() > 0) sb.append('\n');
              sb.append(
                  b.type().equals("LIST_NUMBERED")
                      ? (++n) + ". " + item.asString()
                      : "- " + item.asString());
            }
            parts.add(sb.toString());
          } else {
            parts.add(content);
          }
        }
        case "EMBED" -> {
          // Emit the URL on its own line so it round-trips back to an EMBED block. Content is
          // normally the bare URL; tolerate legacy JSON {url}.
          if (content == null || content.isEmpty()) break;
          JsonNode node = readTreeOrNull(content);
          if (node != null && node.path("url").isString()) {
            parts.add(node.path("url").stringValue());
          } else {
            parts.add(content);
          }
        }
        case "CODE" -> {
          JsonNode node = readTreeOrNull(content);
          if (node != null) {
            String code = node.path("code").isString() ? node.path("code").stringValue() : "";
            String lang = node.path("lang").isString() ? node.path("lang").stringValue() : "";
            String fence = fenceFor(code);
            parts.add(fence + lang + "\n" + code + "\n" + fence);
          }
        }
          // TABLE = raw GFM markdown, CTA_REF = read-only placeholder preserved as-is, PARAGRAPH
          // and anything unknown fall through to verbatim content.
        default -> {
          if (content != null && !content.isEmpty()) parts.add(content);
        }
      }
    }
    return String.join("\n\n", parts);
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

  /** A pipe-led line whose next line is a GFM separator row (`| --- |`) — the start of a table. */
  private static boolean isTableStart(String line, String next) {
    if (!line.stripLeading().startsWith("|") || next == null) return false;
    String t = next.trim();
    return TABLE_SEP.matcher(t).matches() && t.contains("-") && t.contains("|");
  }

  /**
   * A line that is just a single URL (bare, an autolink, or a `[text](url)` link) → that URL.
   * velog-style: ANY standalone parseable http(s) URL on its own line becomes an EMBED card
   * (video→iframe, map→static map, everything else→OG link card — the reader decides). The web's
   * {@code planEmbed} only returns null for unparseable / non-http URLs, so "parseable" is the
   * whole gate here. A URL with surrounding text stays an inline link.
   */
  private static String standaloneEmbedUrl(String line) {
    String t = line.trim();
    Matcher m = AUTOLINK.matcher(t);
    if (!m.matches()) m = LINK_ONLY.matcher(t);
    if (!m.matches()) m = BARE_URL.matcher(t);
    if (!m.matches()) return null;
    String url = m.group(1);
    try {
      URI parsed = new URI(url);
      if (parsed.getHost() == null) return null;
      return url;
    } catch (Exception e) {
      return null;
    }
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

  private static ReplacePostBlocksCommand.BlockInput block(PostBlockType type, String content) {
    return new ReplacePostBlocksCommand.BlockInput(type, content);
  }
}
