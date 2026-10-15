package com.example.short_link.blogimport.application.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.write.ReplacePostBlocksCommand.BlockInput;
import com.example.short_link.post.domain.PostBlockType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HtmlBlockConverterTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String BASE = "https://blog.example.com/posts/1";
  private final HtmlBlockConverter converter = new HtmlBlockConverter(JSON);

  private List<BlockInput> blocks(String html) {
    return converter.blocks(converter.convert(html, BASE), UnaryOperator.identity());
  }

  private static String joined(List<BlockInput> blocks) {
    StringBuilder all = new StringBuilder();
    for (BlockInput block : blocks) {
      all.append(block.type()).append(':').append(block.content()).append('\n');
    }
    return all.toString();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<script>alert(1)</script><p>hi</p>",
        "<p>hi<img src=x onerror=alert(1)></p>",
        "<p><a href=\"javascript:alert(1)\">hi</a></p>",
        "<p><a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">hi</a></p>",
        "<p><img src=\"javascript:alert(1)\">hi</p>",
        "<iframe src=\"https://evil.example/x\"></iframe><p>hi</p>",
        "<svg onload=alert(1)><p>hi</p></svg>",
        "<style>body{display:none}</style><p style=\"x:expression(alert(1))\">hi</p>",
        "<object data=\"https://evil.example/x.swf\"></object><embed src=\"x\"><p>hi</p>",
        "<form action=\"https://evil.example\"><input name=p><p>hi</p></form>",
        "<p onclick=\"alert(1)\" onmouseover=\"alert(1)\">hi</p>",
        "<math><mi xlink:href=\"javascript:alert(1)\">hi</mi></math>",
        "<kurl-embed data-url=\"javascript:alert(1)\"></kurl-embed><p>hi</p>",
        "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\"></noscript><p>hi</p>",
      })
  void scriptsHandlersAndForeignSchemesNeverSurvive(String html) {
    String all = joined(blocks(html));

    assertThat(all).contains("hi");
    assertThat(all.toLowerCase())
        .doesNotContain("<script")
        .doesNotContain("onerror")
        .doesNotContain("onload")
        .doesNotContain("onclick")
        .doesNotContain("javascript:")
        .doesNotContain("data:text")
        .doesNotContain("evil.example")
        .doesNotContain("expression(")
        .doesNotContain("<iframe")
        .doesNotContain("<img");
  }

  @Test
  void markupInTextStaysText() {
    List<BlockInput> blocks =
        blocks(
            "<p>&lt;script&gt;alert(1)&lt;/script&gt; [x](javascript:alert(1)) *star* _u_ `t`</p>"
                + "<p># not a heading</p><p>- not a list</p><p>1. not numbered</p>"
                + "<p>&gt; not a quote</p><p>[!NOTE] not a callout</p>");

    assertThat(blocks).extracting(BlockInput::type).containsOnly(PostBlockType.PARAGRAPH);
    assertThat(blocks.get(0).content())
        .isEqualTo(
            "\\<script\\>alert(1)\\</script\\> \\[x\\](javascript:alert(1)) \\*star\\*"
                + " \\_u\\_ \\`t\\`");
    assertThat(blocks.get(1).content()).isEqualTo("\\# not a heading");
    assertThat(blocks.get(2).content()).isEqualTo("\\- not a list");
    assertThat(blocks.get(3).content()).isEqualTo("1\\. not numbered");
    assertThat(blocks.get(4).content()).isEqualTo("\\> not a quote");
    assertThat(blocks.get(5).content()).isEqualTo("\\[!NOTE\\] not a callout");
  }

  @Test
  void codeBlocksKeepTheirLinesLanguageAndAngleBrackets() {
    List<BlockInput> blocks =
        blocks(
            "<p>before</p><pre><code class=\"language-tsx\">const a = &lt;A b=\"c\" /&gt;;\n\n"
                + "if (a &amp;&amp; b) {\n  return `x`;\n}\n</code></pre><p>after</p>");

    assertThat(blocks)
        .extracting(BlockInput::type)
        .containsExactly(PostBlockType.PARAGRAPH, PostBlockType.CODE, PostBlockType.PARAGRAPH);
    JsonNode code = JSON.readTree(blocks.get(1).content());
    assertThat(code.path("lang").asString()).isEqualTo("tsx");
    assertThat(code.path("code").asString())
        .isEqualTo("const a = <A b=\"c\" />;\n\nif (a && b) {\n  return `x`;\n}");
  }

  @Test
  void tistoryCodeBlocksTakeTheirDeclaredLanguageAndLineBreaks() {
    List<BlockInput> blocks =
        blocks(
            "<pre id=\"code_1\" class=\"java\" data-ke-language=\"Java\" data-ke-type=\"codeblock\">"
                + "<code>int a = 1;<br>int b = 2;</code></pre>"
                + "<pre><code>plain</code></pre>");

    JsonNode first = JSON.readTree(blocks.get(0).content());
    assertThat(first.path("lang").asString()).isEqualTo("java");
    assertThat(first.path("code").asString()).isEqualTo("int a = 1;\nint b = 2;");
    assertThat(JSON.readTree(blocks.get(1).content()).path("lang").isNull()).isTrue();
  }

  @Test
  void headingsListsQuotesDividersAndTablesBecomeTheirBlocks() {
    List<BlockInput> blocks =
        blocks(
            "<h1>Top</h1><h2>Two</h2><h4>Four</h4>"
                + "<ul><li>a<ul><li>a1</li></ul></li><li><p>b</p><p>b2</p></li></ul>"
                + "<ol><li>one</li><li><strong>two</strong></li></ol>"
                + "<blockquote><p>q1</p><p>q2</p></blockquote><hr>"
                + "<table><tr><th>h1</th><th>h|2</th></tr><tr><td>c1</td></tr></table>");

    assertThat(blocks)
        .extracting(BlockInput::type)
        .containsExactly(
            PostBlockType.H1,
            PostBlockType.H2,
            PostBlockType.H3,
            PostBlockType.LIST_BULLET,
            PostBlockType.LIST_NUMBERED,
            PostBlockType.QUOTE,
            PostBlockType.DIVIDER,
            PostBlockType.TABLE);
    assertThat(blocks.get(3).content()).isEqualTo("- a\n  - a1\n- b\\\n  b2");
    assertThat(blocks.get(4).content()).isEqualTo("1. one\n2. **two**");
    assertThat(blocks.get(5).content()).isEqualTo("q1\nq2");
    assertThat(blocks.get(6).content()).isNull();
    assertThat(blocks.get(7).content()).isEqualTo("| h1 | h\\|2 |\n| --- | --- |\n| c1 |  |");
  }

  @Test
  void inlineFormattingLinksAndHardBreaks() {
    List<BlockInput> blocks =
        blocks(
            "<p>A <b>bold</b> and <em>em</em>, <del>gone</del>, <code>a`b</code>,"
                + " <a href=\"/rel\">relative</a> and <a href=\"https://x.example/a b(c)\">x</a>"
                + "<br>next <strong>line<br>wrap</strong></p>");

    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).content())
        .isEqualTo(
            "A **bold** and *em*, ~~gone~~, ``a`b``, [relative](https://blog.example.com/rel)"
                + " and [x](https://x.example/a%20b%28c%29)\\\nnext **line**\\\n**wrap**");
  }

  @Test
  void imagesLeaveParagraphsAndFiguresKeepCaptions() {
    List<BlockInput> blocks =
        blocks(
            "<p>look <a href=\"https://x.example/big.png\"><img src=\"/s.png\" alt=\"small\"></a>"
                + " here</p><figure class=\"imageblock\"><span><img src=\"https://cdn.example/a.png\">"
                + "</span><figcaption>caption</figcaption></figure>"
                + "<figure class=\"imagegridblock\"><img src=\"https://cdn.example/1.png\">"
                + "<img src=\"https://cdn.example/2.png\"></figure>");

    assertThat(blocks)
        .extracting(BlockInput::type)
        .containsExactly(
            PostBlockType.PARAGRAPH,
            PostBlockType.IMAGE,
            PostBlockType.IMAGE,
            PostBlockType.IMAGE,
            PostBlockType.IMAGE);
    assertThat(blocks.get(0).content()).isEqualTo("look here");
    JsonNode inline = JSON.readTree(blocks.get(1).content());
    assertThat(inline.path("url").asString()).isEqualTo("https://blog.example.com/s.png");
    assertThat(inline.path("alt").asString()).isEqualTo("small");
    assertThat(JSON.readTree(blocks.get(2).content()).path("caption").asString())
        .isEqualTo("caption");
    assertThat(JSON.readTree(blocks.get(3).content()).has("caption")).isFalse();
  }

  @Test
  void imageAddressesAreSwappedWhenRehosted() {
    ConvertedBody body = converter.convert("<p><img src=\"https://cdn.example/a.png\"></p>", BASE);

    assertThat(body.imageUrls()).containsExactly("https://cdn.example/a.png");
    List<BlockInput> rehosted =
        converter.blocks(body, url -> url.replace("cdn.example", "img.kurl.me"));
    assertThat(JSON.readTree(rehosted.get(0).content()).path("url").asString())
        .isEqualTo("https://img.kurl.me/a.png");
  }

  @Test
  void youtubeIframesBecomeEmbedsAndOtherIframesVanish() {
    List<BlockInput> blocks =
        blocks(
            "<p>watch</p><iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ?rel=0\"></iframe>"
                + "<iframe src=\"https://player.vimeo.com/video/1\"></iframe>"
                + "<p><span><iframe src=\"//www.youtube-nocookie.com/embed/abcdefghijk\"></iframe>"
                + "</span></p>");

    assertThat(blocks)
        .extracting(BlockInput::type)
        .containsExactly(PostBlockType.PARAGRAPH, PostBlockType.EMBED, PostBlockType.EMBED);
    assertThat(blocks.get(1).content()).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    assertThat(blocks.get(2).content()).isEqualTo("https://www.youtube.com/watch?v=abcdefghijk");
  }

  @Test
  void blankParagraphsAndWrappersLeaveNothingBehind() {
    List<BlockInput> blocks =
        blocks(
            "<div><p>&nbsp;</p><section><p>kept</p></section><span>loose <b>text</b></span></div>"
                + "<p><br></p>");

    assertThat(blocks).extracting(BlockInput::content).containsExactly("kept", "loose **text**");
  }

  @Test
  void blockCountAndCodeLengthStayWithinTheEditorsLimits() {
    StringBuilder many = new StringBuilder();
    for (int i = 0; i < HtmlBlockConverter.MAX_BLOCKS + 20; i++) {
      many.append("<p>p").append(i).append("</p>");
    }
    assertThat(blocks(many.toString())).hasSize(HtmlBlockConverter.MAX_BLOCKS);

    String longCode = "\"\\".repeat(60_000);
    List<BlockInput> code = blocks("<pre><code>" + longCode + "</code></pre>");
    assertThat(code.get(0).content().length())
        .isLessThanOrEqualTo(HtmlBlockConverter.MAX_BLOCK_CHARS);
  }

  @Test
  void summariesEndInAnEllipsisAndKeepTheFirstImage() {
    HtmlBlockConverter.Summary summary =
        converter.summarize(
            "요약 끝 부분의....... <img src=\"https://blogthumb.example/a.png?type=s3\">", BASE);

    assertThat(summary.text()).isEqualTo("요약 끝 부분의…");
    assertThat(summary.imageUrl()).isEqualTo("https://blogthumb.example/a.png?type=s3");
    assertThat(converter.summarize("<p>no image</p>", BASE).imageUrl()).isNull();
  }

  @Test
  void excerptsCutAtAWordNearTheLimit() {
    String words = "단어 ".repeat(150);

    String excerpt = HtmlBlockConverter.excerptOf(words);

    assertThat(excerpt).endsWith("…");
    assertThat(excerpt.codePointCount(0, excerpt.length()))
        .isLessThanOrEqualTo(HtmlBlockConverter.EXCERPT_LENGTH);
    assertThat(HtmlBlockConverter.excerptOf(" short  text ")).isEqualTo("short text");
    assertThat(HtmlBlockConverter.excerptOf("가".repeat(300)))
        .hasSize(HtmlBlockConverter.EXCERPT_LENGTH);
  }

  @Test
  void aPlainParagraphIsEscapedLikeConvertedText() {
    assertThat(HtmlBlockConverter.paragraphOf("  # *hello*  ")).isEqualTo("\\# \\*hello\\*");
  }

  @Test
  void markupDetectionTellsFullPostsFromSummaries() {
    assertThat(converter.hasMarkup("<p>full</p>")).isTrue();
    assertThat(converter.hasMarkup("just a summary...")).isFalse();
    assertThat(converter.hasMarkup("")).isFalse();
    assertThat(converter.hasMarkup(null)).isFalse();
  }

  @Test
  void realFeedsConvertWithoutLosingCode() throws Exception {
    FeedDocument velog =
        RssFeedParser.parse(Files.readString(Path.of("src/test/resources/blog-import/velog.xml")))
            .orElseThrow();
    FeedEntry entry = velog.entries().get(1);

    List<BlockInput> blocks = blocks(entry.html());

    BlockInput code =
        blocks.stream().filter(b -> b.type() == PostBlockType.CODE).findFirst().orElseThrow();
    JsonNode node = JSON.readTree(code.content());
    assertThat(node.path("lang").asString()).isEqualTo("jsx");
    assertThat(node.path("code").asString())
        .startsWith("import { json, LoaderFunction } from '@remix-run/node'\n")
        .contains("return json(items)");
    assertThat(blocks).extracting(BlockInput::type).contains(PostBlockType.H2, PostBlockType.IMAGE);

    FeedDocument tistory =
        RssFeedParser.parse(Files.readString(Path.of("src/test/resources/blog-import/tistory.xml")))
            .orElseThrow();
    List<BlockInput> notice = blocks(tistory.entries().get(3).html());
    assertThat(notice).extracting(BlockInput::type).contains(PostBlockType.TABLE);
    assertThat(
            notice.stream()
                .filter(b -> b.type() == PostBlockType.TABLE)
                .findFirst()
                .orElseThrow()
                .content())
        .startsWith("| **변경 전** | **변경 후** |\n| --- | --- |")
        .contains("**카카오 약관 적용** - 카카오 통합서비스약관");
  }
}
