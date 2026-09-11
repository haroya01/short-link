package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 한 문서의 문법 인식 순서와 커서를 소유한다. 인스턴스는 변환 요청마다 생성한다. */
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
  // 표준 마크다운 image title `![alt](url "캡션")` 의 title 을 캡션으로 분리 캡처(group 3). The title
  // allows escaped quotes (`\"`) — the web editor backslash-escapes a `"` inside the caption, so a
  // caption like `she said "hi"` serializes as `"she said \"hi\""`; without honoring the escape the
  // whole image match failed and the image fell back to a literal-text PARAGRAPH.
  private static final Pattern IMAGE =
      Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?\\)");
  // Backslash escapes inside an image title (`\"` → `"`, `\\` → `\`), undone when reading a caption
  // back out. Mirrors the web's unescapeTitle.
  private static final Pattern TITLE_ESCAPE = Pattern.compile("\\\\([\"\\\\])");
  private static final Pattern AUTOLINK = Pattern.compile("^<(https?://[^>\\s]+)>$");
  private static final Pattern LINK_ONLY =
      Pattern.compile("^\\[[^\\]]*\\]\\((https?://[^)\\s]+)\\)$");
  private static final Pattern BARE_URL = Pattern.compile("^(https?://\\S+)$");
  // A URL path ending in one of the image formats the upload/import pipeline handles — used to send
  // a standalone bare image URL to an IMAGE block instead of a link-preview EMBED.
  private static final Pattern IMAGE_EXT =
      Pattern.compile("\\.(?:jpe?g|png|gif|webp)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIST_START = Pattern.compile("^(?:[-*]|\\d+\\.)\\s+.*");
  private static final Pattern LIST_CONT = Pattern.compile("^\\s*(?:[-*]|\\d+\\.)\\s+.*");
  private static final Pattern INDENTED = Pattern.compile("^\\s+\\S.*");
  private static final Pattern TABLE_SEP = Pattern.compile("^[\\s|:-]+$");
  private static final Pattern PARA_BREAK =
      Pattern.compile("^(#{1,3}\\s|>\\s|!\\[|[-*]\\s|\\d+\\.\\s).*");

  // Medium-style per-image width, carried as an alt-text marker prefix (the only metadata that
  // survives the markdown round-trip). Stripped before storage, re-attached on serialize.
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
      return true;
    }
    return false;
  }

  private boolean readTable() {
    String line = lines[i];
    // GFM table (header row + "| --- |" separator + body rows) → raw markdown in one block.
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
      return true;
    }
    return false;
  }

  private record Image(String alt, String url, String caption) {}

  private boolean readImages() {
    String line = lines[i];
    // One OR MORE images on a line (a side-by-side «half» pair serializes adjacent) → one IMAGE
    // block each. Only when the line is *nothing but* images.
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
    // A standalone bare image URL (e.g. an external image pasted on its own line) → IMAGE block,
    // so it renders as the image and not a link-preview card. Must run before the embed check,
    // which would otherwise claim every standalone http(s) URL.
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
      return true;
    }
    return false;
  }

  private void readParagraph() {
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
        && standaloneImageUrl(lines[i]) == null
        && standaloneEmbedUrl(lines[i]) == null) {
      paraLines.add(lines[i]);
      i++;
    }
    blocks.add(block(PostBlockType.PARAGRAPH, String.join("\n", paraLines)));
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

  /**
   * A line that is just a bare (or {@code <autolink>}) URL pointing at an image file (by extension)
   * → that URL, so a pasted external image renders as an IMAGE block instead of a link-preview
   * EMBED. A labeled {@code [text](url)} link is deliberately left to the embed path (the author
   * meant a link, not an image). Mirrors the web's {@code standaloneImageUrl}.
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

  /** Undo the image-title escaping ({@code \"} → {@code "}, {@code \\} → {@code \}). */
  private static String unescapeTitle(String s) {
    return TITLE_ESCAPE.matcher(s).replaceAll("$1");
  }

  private static ReplacePostBlocksCommand.BlockInput block(PostBlockType type, String content) {
    return new ReplacePostBlocksCommand.BlockInput(type, content);
  }
}
