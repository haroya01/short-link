package com.example.short_link.blogimport.application.feed;

import com.example.short_link.post.application.write.ReplacePostBlocksCommand.BlockInput;
import com.example.short_link.post.domain.PostBlockType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

// Remote HTML never reaches a reader as HTML: it is cleaned to the safelist below (no scripts,
// styles, iframes, event or style attributes, non-http(s) URLs), then rewritten as blocks whose
// text is escaped markdown.
@Component
public class HtmlBlockConverter {

  public static final int MAX_BLOCKS = 500;
  public static final int MAX_BLOCK_CHARS = 100_000;
  public static final int EXCERPT_LENGTH = 200;

  private static final String EMBED_TAG = "kurl-embed";

  private static final Safelist SAFELIST =
      new Safelist()
          .addTags(
              "p",
              "br",
              "h1",
              "h2",
              "h3",
              "h4",
              "h5",
              "h6",
              "ul",
              "ol",
              "li",
              "blockquote",
              "pre",
              "code",
              "strong",
              "b",
              "em",
              "i",
              "u",
              "s",
              "del",
              "strike",
              "a",
              "img",
              "figure",
              "figcaption",
              "table",
              "thead",
              "tbody",
              "tfoot",
              "tr",
              "th",
              "td",
              "hr",
              "span",
              "div",
              "section",
              "article",
              EMBED_TAG)
          .addAttributes("a", "href")
          .addAttributes("img", "src", "alt")
          .addAttributes("pre", "class", "data-ke-language")
          .addAttributes("code", "class")
          .addAttributes(EMBED_TAG, "data-url")
          .addProtocols("a", "href", "http", "https")
          .addProtocols("img", "src", "http", "https");

  private static final Pattern YOUTUBE_EMBED =
      Pattern.compile(
          "^(?:https?:)?//(?:www\\.)?(?:youtube\\.com|youtube-nocookie\\.com)/embed/([A-Za-z0-9_-]{11})");
  private static final Pattern YOUTUBE_WATCH =
      Pattern.compile("^https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}$");
  private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z0-9+#_.-]{1,32}");
  private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0]+");
  private static final Pattern ORDERED_MARKER = Pattern.compile("^(\\d{1,9})([.)])");
  private static final Pattern TRAILING_DOTS = Pattern.compile("(?:\\s*(?:\\.{3,}|…+))+$");
  private static final Set<String> BLOCK_TAGS =
      Set.of(
          "p",
          "div",
          "section",
          "article",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "ul",
          "ol",
          "blockquote",
          "pre",
          "table",
          "figure",
          "hr");
  private static final String HARD_BREAK = "\\\n";

  private final JsonMapper json;

  public HtmlBlockConverter(JsonMapper json) {
    this.json = json;
  }

  public ConvertedBody convert(String html, String baseUrl) {
    Element body = clean(html, baseUrl).body();
    Walker walker = new Walker();
    walker.children(body);
    walker.flush();
    return new ConvertedBody(walker.pieces, excerpt(body));
  }

  public List<BlockInput> blocks(ConvertedBody body, UnaryOperator<String> imageUrl) {
    List<BlockInput> blocks = new ArrayList<>(body.pieces().size());
    for (ConvertedBody.Piece piece : body.pieces()) {
      if (piece.image() == null) {
        blocks.add(piece.block());
        continue;
      }
      ConvertedBody.Image image = piece.image();
      ObjectNode node = json.createObjectNode();
      node.put("url", imageUrl.apply(image.url()));
      node.put("alt", image.alt());
      if (image.caption() != null && !image.caption().isBlank()) {
        node.put("caption", image.caption());
      }
      blocks.add(new BlockInput(PostBlockType.IMAGE, json.writeValueAsString(node)));
    }
    return blocks;
  }

  public Summary summarize(String html, String baseUrl) {
    Element body = clean(html, baseUrl).body();
    Element img = body.selectFirst("img[src]");
    String text = TRAILING_DOTS.matcher(collapse(body.text())).replaceFirst("…");
    return new Summary(text, img == null ? null : img.attr("src"));
  }

  public boolean hasMarkup(String html) {
    if (html == null || html.isBlank()) {
      return false;
    }
    return Jsoup.parseBodyFragment(html)
            .body()
            .selectFirst(
                "p, div, br, h1, h2, h3, h4, h5, h6, ul, ol, blockquote, pre, table, figure")
        != null;
  }

  public static String paragraphOf(String text) {
    return clip(paragraphContent(escape(collapse(text))));
  }

  public static String excerptOf(String text) {
    String value = collapse(text);
    if (value.codePointCount(0, value.length()) <= EXCERPT_LENGTH) {
      return value;
    }
    int end = value.offsetByCodePoints(0, EXCERPT_LENGTH - 1);
    int space = value.lastIndexOf(' ', end);
    return value.substring(0, space > EXCERPT_LENGTH / 2 ? space : end).strip() + "…";
  }

  public record Summary(String text, String imageUrl) {}

  private static Document clean(String html, String baseUrl) {
    Document dirty = Jsoup.parseBodyFragment(html == null ? "" : html, baseUrl);
    for (Element iframe : dirty.select("iframe[src]")) {
      Matcher youtube = YOUTUBE_EMBED.matcher(iframe.attr("src").strip());
      if (youtube.find()) {
        iframe.replaceWith(
            new Element(EMBED_TAG)
                .attr("data-url", "https://www.youtube.com/watch?v=" + youtube.group(1)));
      }
    }
    return new Cleaner(SAFELIST).clean(dirty);
  }

  private static String excerpt(Element body) {
    Element copy = body.clone();
    copy.select("pre, table, figcaption, " + EMBED_TAG).remove();
    return excerptOf(copy.text());
  }

  private static String collapse(String text) {
    return text == null ? "" : WHITESPACE.matcher(text).replaceAll(" ").strip();
  }

  static String escape(String text) {
    StringBuilder out = new StringBuilder(text.length() + 8);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\', '`', '*', '_', '[', ']', '<', '>', '~', '&', '|' -> out.append('\\').append(c);
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  static String safeLineStart(String line) {
    String value = line.stripLeading();
    if (value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    if (first == '#' || first == '-' || first == '+' || first == '=') {
      return "\\" + value;
    }
    Matcher ordered = ORDERED_MARKER.matcher(value);
    if (ordered.find()) {
      return ordered.group(1) + "\\" + value.substring(ordered.group(1).length());
    }
    return value;
  }

  private static String inlineCode(String code) {
    String value = collapse(code);
    if (value.isEmpty()) {
      return "";
    }
    int longest = 0;
    Matcher run = Pattern.compile("`+").matcher(value);
    while (run.find()) {
      longest = Math.max(longest, run.group().length());
    }
    String fence = "`".repeat(longest + 1);
    String pad = value.startsWith("`") || value.endsWith("`") ? " " : "";
    return fence + pad + value + pad + fence;
  }

  private static String linkTarget(String href) {
    return href.replace(" ", "%20")
        .replace("(", "%28")
        .replace(")", "%29")
        .replace("<", "%3C")
        .replace(">", "%3E");
  }

  private final class Walker {
    private final List<ConvertedBody.Piece> pieces = new ArrayList<>();
    private final List<ConvertedBody.Image> pending = new ArrayList<>();
    private StringBuilder paragraph = new StringBuilder();

    void children(Element parent) {
      for (Node child : parent.childNodes()) {
        node(child);
      }
    }

    void node(Node node) {
      if (node instanceof TextNode text) {
        appendText(paragraph, text.getWholeText());
        return;
      }
      if (!(node instanceof Element element)) {
        return;
      }
      switch (element.normalName()) {
        case "p" -> {
          flush();
          if (containsBlock(element)) {
            children(element);
          } else {
            inline(paragraph, element);
          }
          flush();
        }
        case "h1" -> heading(PostBlockType.H1, element);
        case "h2" -> heading(PostBlockType.H2, element);
        case "h3", "h4", "h5", "h6" -> heading(PostBlockType.H3, element);
        case "ul" -> list(element, false);
        case "ol" -> list(element, true);
        case "blockquote" -> quote(element);
        case "pre" -> code(element);
        case "hr" -> {
          flush();
          add(PostBlockType.DIVIDER, null);
        }
        case "img" -> {
          flush();
          image(element, null);
        }
        case "figure" -> figure(element);
        case "table" -> table(element);
        case "br" -> paragraph.append(HARD_BREAK);
        case EMBED_TAG -> embed(element);
        case "div", "section", "article" -> {
          flush();
          children(element);
          flush();
        }
        default -> {
          if (containsBlock(element)) {
            flush();
            children(element);
            flush();
          } else {
            inlineElement(paragraph, element);
          }
        }
      }
    }

    void flush() {
      String content = paragraphContent(paragraph.toString());
      paragraph = new StringBuilder();
      if (!content.isEmpty()) {
        add(PostBlockType.PARAGRAPH, clip(content));
      }
      for (ConvertedBody.Image image : pending) {
        addImage(image);
      }
      pending.clear();
    }

    private void heading(PostBlockType type, Element element) {
      flush();
      StringBuilder text = new StringBuilder();
      inline(text, element);
      String content = safeLineStart(collapse(text.toString().replace(HARD_BREAK, " ")));
      if (!content.isEmpty()) {
        add(type, clip(content));
      }
      flushImages();
    }

    private void list(Element element, boolean ordered) {
      flush();
      List<String> lines = new ArrayList<>();
      renderList(element, ordered, "", lines);
      if (!lines.isEmpty()) {
        add(
            ordered ? PostBlockType.LIST_NUMBERED : PostBlockType.LIST_BULLET,
            clip(String.join("\n", lines)));
      }
      flushImages();
    }

    private void renderList(Element list, boolean ordered, String indent, List<String> lines) {
      int number = 1;
      for (Element item : list.children()) {
        if (!item.normalName().equals("li")) {
          continue;
        }
        String marker = ordered ? (number++) + ". " : "- ";
        String continuation = indent + " ".repeat(marker.length());
        StringBuilder text = new StringBuilder();
        List<Element> nested = new ArrayList<>();
        for (Node child : item.childNodes()) {
          if (child instanceof Element e
              && (e.normalName().equals("ul") || e.normalName().equals("ol"))) {
            nested.add(e);
          } else if (child instanceof Element e && BLOCK_TAGS.contains(e.normalName())) {
            if (!text.isEmpty()) {
              text.append(HARD_BREAK);
            }
            inline(text, e);
          } else {
            inlineNode(text, child);
          }
        }
        String content = paragraphContent(text.toString());
        lines.add(indent + marker + content.replace("\n", "\n" + continuation));
        for (Element sub : nested) {
          renderList(sub, sub.normalName().equals("ol"), continuation, lines);
        }
      }
    }

    private void quote(Element element) {
      flush();
      List<String> lines = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      for (Node child : element.childNodes()) {
        if (child instanceof Element e && BLOCK_TAGS.contains(e.normalName())) {
          lines.add(current.toString());
          current = new StringBuilder();
          inline(current, e);
          lines.add(current.toString());
          current = new StringBuilder();
        } else {
          inlineNode(current, child);
        }
      }
      lines.add(current.toString());
      List<String> kept = new ArrayList<>();
      for (String line : lines) {
        for (String part : line.split(Pattern.quote(HARD_BREAK))) {
          String value = safeLineStart(collapse(part));
          if (!value.isEmpty()) {
            kept.add(value);
          }
        }
      }
      if (!kept.isEmpty()) {
        add(PostBlockType.QUOTE, clip(String.join("\n", kept)));
      }
      flushImages();
    }

    private void code(Element pre) {
      flush();
      StringBuilder text = new StringBuilder();
      preText(text, pre);
      String code = text.toString().replace("\r\n", "\n");
      if (code.endsWith("\n")) {
        code = code.substring(0, code.length() - 1);
      }
      if (code.isBlank()) {
        return;
      }
      String lang = language(pre);
      String content = codeJson(lang, code);
      while (content.length() > MAX_BLOCK_CHARS) {
        code = code.substring(0, code.length() * 9 / 10);
        content = codeJson(lang, code);
      }
      add(PostBlockType.CODE, content);
    }

    private String codeJson(String lang, String code) {
      ObjectNode node = json.createObjectNode();
      if (lang == null) {
        node.putNull("lang");
      } else {
        node.put("lang", lang);
      }
      node.put("code", code);
      return json.writeValueAsString(node);
    }

    private void preText(StringBuilder out, Node node) {
      for (Node child : node.childNodes()) {
        if (child instanceof TextNode text) {
          out.append(text.getWholeText());
        } else if (child instanceof Element e) {
          if (e.normalName().equals("br")) {
            out.append('\n');
          } else {
            preText(out, e);
          }
        }
      }
    }

    private String language(Element pre) {
      String declared = pre.attr("data-ke-language").strip();
      if (LANGUAGE.matcher(declared).matches()) {
        return declared.toLowerCase(Locale.ROOT);
      }
      List<String> classes = new ArrayList<>();
      Element code = pre.selectFirst("> code");
      if (code != null) {
        classes.addAll(code.classNames());
      }
      classes.addAll(pre.classNames());
      for (String name : classes) {
        for (String prefix : List.of("language-", "lang-")) {
          if (name.startsWith(prefix)) {
            String value = name.substring(prefix.length());
            if (LANGUAGE.matcher(value).matches()) {
              return value.toLowerCase(Locale.ROOT);
            }
          }
        }
      }
      return null;
    }

    private void figure(Element figure) {
      flush();
      List<Element> imgs = figure.select("img");
      if (imgs.isEmpty() || figure.selectFirst("table, pre, ul, ol, blockquote") != null) {
        children(figure);
        flush();
        return;
      }
      Element caption = figure.selectFirst("figcaption");
      String text = caption == null ? null : collapse(caption.text());
      for (int i = 0; i < imgs.size(); i++) {
        image(imgs.get(i), i == imgs.size() - 1 ? text : null);
      }
    }

    private void image(Element img, String caption) {
      ConvertedBody.Image image = imageOf(img, caption);
      if (image != null) {
        addImage(image);
      }
    }

    private ConvertedBody.Image imageOf(Element img, String caption) {
      String src = img.attr("src").strip();
      if (src.isEmpty()) {
        return null;
      }
      String alt = collapse(img.attr("alt"));
      if (alt.length() > 300) {
        alt = alt.substring(0, 300);
      }
      return new ConvertedBody.Image(
          src, alt, caption == null || caption.isBlank() ? null : caption);
    }

    private void table(Element table) {
      flush();
      List<List<String>> rows = new ArrayList<>();
      int width = 0;
      for (Element row : table.select("tr")) {
        List<String> cells = new ArrayList<>();
        for (Element cell : row.children()) {
          if (!cell.normalName().equals("td") && !cell.normalName().equals("th")) {
            continue;
          }
          StringBuilder text = new StringBuilder();
          inline(text, cell);
          cells.add(collapse(text.toString().replace(HARD_BREAK, " ")));
        }
        if (!cells.isEmpty()) {
          rows.add(cells);
          width = Math.max(width, cells.size());
        }
      }
      if (rows.isEmpty()) {
        flushImages();
        return;
      }
      List<String> lines = new ArrayList<>();
      for (int r = 0; r < rows.size(); r++) {
        List<String> cells = new ArrayList<>(rows.get(r));
        while (cells.size() < width) {
          cells.add("");
        }
        lines.add("| " + String.join(" | ", cells) + " |");
        if (r == 0) {
          lines.add("|" + " --- |".repeat(width));
        }
      }
      add(PostBlockType.TABLE, clip(String.join("\n", lines)));
      flushImages();
    }

    private void embed(Element element) {
      String url = element.attr("data-url");
      if (YOUTUBE_WATCH.matcher(url).matches()) {
        flush();
        add(PostBlockType.EMBED, url);
      }
    }

    private void inline(StringBuilder out, Element element) {
      for (Node child : element.childNodes()) {
        inlineNode(out, child);
      }
    }

    private void inlineNode(StringBuilder out, Node node) {
      if (node instanceof TextNode text) {
        appendText(out, text.getWholeText());
      } else if (node instanceof Element element) {
        inlineElement(out, element);
      }
    }

    private void inlineElement(StringBuilder out, Element element) {
      switch (element.normalName()) {
        case "br" -> out.append(HARD_BREAK);
        case "strong", "b" -> wrap(out, element, "**");
        case "em", "i" -> wrap(out, element, "*");
        case "s", "del", "strike" -> wrap(out, element, "~~");
        case "code" -> out.append(inlineCode(element.wholeText()));
        case "a" -> link(out, element);
        case "img" -> {
          ConvertedBody.Image image = imageOf(element, null);
          if (image != null) {
            pending.add(image);
          }
        }
        case "pre" -> out.append(inlineCode(element.wholeText()));
        case EMBED_TAG -> {}
        default -> {
          if (BLOCK_TAGS.contains(element.normalName()) && !out.isEmpty()) {
            out.append(HARD_BREAK);
          }
          inline(out, element);
        }
      }
    }

    private void wrap(StringBuilder out, Element element, String marker) {
      StringBuilder inner = new StringBuilder();
      inline(inner, element);
      String[] lines = inner.toString().split(Pattern.quote(HARD_BREAK), -1);
      for (int i = 0; i < lines.length; i++) {
        if (i > 0) {
          out.append(HARD_BREAK);
        }
        String value = lines[i];
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
          out.append(value);
          continue;
        }
        if (Character.isWhitespace(value.charAt(0))) {
          out.append(' ');
        }
        out.append(marker).append(trimmed).append(marker);
        if (Character.isWhitespace(value.charAt(value.length() - 1))) {
          out.append(' ');
        }
      }
    }

    private void link(StringBuilder out, Element element) {
      StringBuilder inner = new StringBuilder();
      inline(inner, element);
      String text = collapse(inner.toString().replace(HARD_BREAK, " "));
      String href = element.attr("href").strip();
      if (href.isEmpty()) {
        out.append(text);
        return;
      }
      if (text.isEmpty() && element.selectFirst("img") != null) {
        return;
      }
      out.append('[')
          .append(text.isEmpty() ? escape(href) : text)
          .append("](")
          .append(linkTarget(href))
          .append(')');
    }

    private void appendText(StringBuilder out, String raw) {
      String text = WHITESPACE.matcher(raw).replaceAll(" ");
      if (text.isEmpty()) {
        return;
      }
      out.append(escape(text));
    }

    private void add(PostBlockType type, String content) {
      if (pieces.size() < MAX_BLOCKS) {
        pieces.add(new ConvertedBody.Piece(new BlockInput(type, content), null));
      }
    }

    private void addImage(ConvertedBody.Image image) {
      if (pieces.size() < MAX_BLOCKS) {
        pieces.add(new ConvertedBody.Piece(null, image));
      }
    }

    private void flushImages() {
      for (ConvertedBody.Image image : pending) {
        addImage(image);
      }
      pending.clear();
    }

    private boolean containsBlock(Element element) {
      for (Element descendant : element.getAllElements()) {
        if (descendant != element
            && (BLOCK_TAGS.contains(descendant.normalName())
                || descendant.normalName().equals(EMBED_TAG))) {
          return true;
        }
      }
      return false;
    }
  }

  private static String paragraphContent(String raw) {
    List<String> lines = new ArrayList<>();
    for (String part : raw.split(Pattern.quote(HARD_BREAK))) {
      String value = safeLineStart(collapse(part));
      if (!value.isEmpty()) {
        lines.add(value);
      }
    }
    return String.join(HARD_BREAK, lines);
  }

  private static String clip(String content) {
    return content.length() > MAX_BLOCK_CHARS ? content.substring(0, MAX_BLOCK_CHARS) : content;
  }
}
