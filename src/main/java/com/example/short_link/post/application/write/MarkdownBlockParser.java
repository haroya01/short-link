package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 인스턴스마다 한 변환 요청의 커서를 보유한다. */
final class MarkdownBlockParser {
  private final JsonMapper json;
  private final String[] lines;
  private final List<ReplacePostBlocksCommand.BlockInput> blocks = new ArrayList<>();
  private int i;

  MarkdownBlockParser(JsonMapper json, String markdown) {
    this.json = json;
    this.lines = markdown.replace("\r\n", "\n").split("\n", -1);
  }

  private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})(.*)$");
  private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
  private static final Pattern QUOTE = Pattern.compile("^>\\s*(.*)$");
  // Image titles allow escaped quotes so editor captions round-trip through markdown.
  private static final Pattern IMAGE =
      Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?\\)");
  // Mirrors the web editor's unescapeTitle for escaped quotes and backslashes.
  private static final Pattern TITLE_ESCAPE = Pattern.compile("\\\\([\"\\\\])");
  private static final Pattern AUTOLINK = Pattern.compile("^<(https?://[^>\\s]+)>$");
  private static final Pattern LINK_ONLY =
      Pattern.compile("^\\[[^\\]]*\\]\\((https?://[^)\\s]+)\\)$");
  private static final Pattern BARE_URL = Pattern.compile("^(https?://\\S+)$");
  // Standalone image URLs become IMAGE blocks before the generic embed rule can claim them.
  private static final Pattern IMAGE_EXT =
      Pattern.compile("\\.(?:jpe?g|png|gif|webp)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIST_START = Pattern.compile("^(?:[-*]|\\d+\\.)\\s+.*");
  private static final Pattern LIST_CONT = Pattern.compile("^\\s*(?:[-*]|\\d+\\.)\\s+.*");
  private static final Pattern INDENTED = Pattern.compile("^\\s+\\S.*");
  private static final Pattern TABLE_SEP = Pattern.compile("^[\\s|:-]+$");
  private static final Pattern PARA_BREAK =
      Pattern.compile("^(#{1,3}\\s|>\\s|!\\[|[-*]\\s|\\d+\\.\\s).*");

  // The alt-text width marker survives markdown round-trips; strip on read and restore on write.
  private static final String[] WIDTHS = {"wide", "full", "half"};

  List<ReplacePostBlocksCommand.BlockInput> parse() {
    while (i < lines.length) {
      if (lines[i].trim().isEmpty()) {
        i++;
        continue;
      }
      // 문법 우선순위: 코드/표를 먼저 소비하고, 이미지 URL은 일반 임베드보다 먼저 해석한다.
      if (readFence()
          || readTable()
          || readDivider()
          || readHeading()
          || readQuote()
          || readImages()
          || readImageUrl()
          || readEmbed()
          || readList()) continue;
      readParagraph();
    }
    return blocks;
  }

  private boolean readFence() {
    String line = lines[i];
    // Consume the whole fence so line-based rules cannot split code at blank or markdown-like
    // lines.
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
      return true;
    }
    return false;
  }

  private boolean readTable() {
    String line = lines[i];
    if (isTableStart(line, i + 1 < lines.length ? lines[i + 1] : null)) {
      List<String> rows = new ArrayList<>();
      while (i < lines.length && lines[i].stripLeading().startsWith("|")) {
        rows.add(lines[i]);
        i++;
      }
      blocks.add(block(PostBlockType.TABLE, String.join("\n", rows)));
      return true;
    }
    return false;
  }

  private boolean readDivider() {
    String line = lines[i];
    if (line.trim().equals("---")) {
      blocks.add(block(PostBlockType.DIVIDER, null));
      i++;
      return true;
    }
    return false;
  }

  private boolean readHeading() {
    String line = lines[i];
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
      return true;
    }
    return false;
  }

  private boolean readQuote() {
    String line = lines[i];
    Matcher quote = QUOTE.matcher(line);
    if (quote.matches()) {
      // Coalesce consecutive quote lines so one quote does not render as several adjacent boxes.
      StringBuilder quoteLines = new StringBuilder(quote.group(1));
      i++;
      while (i < lines.length) {
        Matcher qm = QUOTE.matcher(lines[i]);
        if (!qm.matches()) break;
        quoteLines.append('\n').append(qm.group(1));
        i++;
      }
      blocks.add(block(PostBlockType.QUOTE, quoteLines.toString().trim()));
      return true;
    }
    return false;
  }

  private record Image(String alt, String url, String caption) {}

  private boolean readImages() {
    String line = lines[i];
    // Side-by-side half-width images serialize adjacently; accept a line containing only images.
    Matcher img = IMAGE.matcher(line);
    List<Image> images = new ArrayList<>();
    while (img.find()) {
      images.add(new Image(img.group(1), img.group(2), img.group(3)));
    }
    if (!images.isEmpty() && IMAGE.matcher(line).replaceAll("").trim().isEmpty()) {
      for (Image im : images) {
        ObjectNode node = json.createObjectNode();
        node.put("url", im.url());
        String alt = im.alt();
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
        if (im.caption() != null) {
          String caption = unescapeTitle(im.caption()).trim();
          if (!caption.isEmpty()) {
            node.put("caption", caption);
          }
        }
        blocks.add(block(PostBlockType.IMAGE, json.writeValueAsString(node)));
      }
      i++;
      return true;
    }
    return false;
  }

  private boolean readImageUrl() {
    String line = lines[i];
    // Must precede the embed rule, which accepts every standalone HTTP(S) URL.
    String imageUrl = standaloneImageUrl(line);
    if (imageUrl != null) {
      ObjectNode node = json.createObjectNode();
      node.put("url", imageUrl);
      node.put("alt", "");
      blocks.add(block(PostBlockType.IMAGE, json.writeValueAsString(node)));
      i++;
      return true;
    }
    return false;
  }

  private boolean readEmbed() {
    String line = lines[i];
    String embedUrl = standaloneEmbedUrl(line);
    if (embedUrl != null) {
      blocks.add(block(PostBlockType.EMBED, embedUrl));
      i++;
      return true;
    }
    return false;
  }

  private boolean readList() {
    String line = lines[i];
    // Keep the complete list as markdown to preserve nesting; its first line sets the block type.
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
      return true;
    }
    return false;
  }

  private void readParagraph() {
    // Consume the current line first to guarantee progress when no earlier rule accepts it.
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
        && standaloneImageUrl(lines[i]) == null
        && standaloneEmbedUrl(lines[i]) == null) {
      paraLines.add(lines[i]);
      i++;
    }
    blocks.add(block(PostBlockType.PARAGRAPH, String.join("\n", paraLines)));
  }

  private static boolean isTableStart(String line, String next) {
    if (!line.stripLeading().startsWith("|") || next == null) return false;
    String t = next.trim();
    return TABLE_SEP.matcher(t).matches() && t.contains("-") && t.contains("|");
  }

  /**
   * Any standalone parseable HTTP(S) URL becomes an embed, matching the web editor. A URL
   * surrounded by text remains an inline link.
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

  /**
   * Labeled {@code [text](url)} image links stay on the embed path: the author requested a link.
   * Bare URLs and autolinks with image extensions become IMAGE blocks, matching the web editor.
   */
  private static String standaloneImageUrl(String line) {
    String t = line.trim();
    Matcher m = AUTOLINK.matcher(t);
    if (!m.matches()) m = BARE_URL.matcher(t);
    if (!m.matches()) return null;
    String url = m.group(1);
    try {
      URI parsed = new URI(url);
      if (parsed.getHost() == null) return null;
      String path = parsed.getPath();
      return path != null && IMAGE_EXT.matcher(path).find() ? url : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static String unescapeTitle(String s) {
    return TITLE_ESCAPE.matcher(s).replaceAll("$1");
  }

  private static ReplacePostBlocksCommand.BlockInput block(PostBlockType type, String content) {
    return new ReplacePostBlocksCommand.BlockInput(type, content);
  }
}
