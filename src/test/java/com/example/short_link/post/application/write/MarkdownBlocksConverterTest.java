package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.application.write.ReplacePostBlocksCommand.BlockInput;
import com.example.short_link.post.domain.PostBlockType;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ported from the web editor's markdown-to-blocks.test.ts — the two converters must stay
 * behaviorally identical, so this mirrors that spec case-for-case.
 */
class MarkdownBlocksConverterTest {

  private final MarkdownBlocksConverter converter =
      new MarkdownBlocksConverter(JsonMapper.builder().build());

  private List<BlockInput> toBlocks(String md) {
    return converter.toBlocks(md);
  }

  private String roundTrip(List<BlockInput> blocks) {
    return converter.toMarkdown(
        blocks.stream()
            .map(b -> new PostBlockView(null, b.type().name(), b.content(), null))
            .toList());
  }

  @Test
  void returnsEmptyListForEmptyInput() {
    assertThat(toBlocks("")).isEmpty();
    assertThat(toBlocks("   \n  \n")).isEmpty();
    assertThat(toBlocks(null)).isEmpty();
  }

  // Regression pair from the web: image lines the image rule doesn't fully consume must still
  // advance and fall back to PARAGRAPH (the web version once looped forever here).
  @Test
  void doesNotHangOnImageLineWithTrailingCaption() {
    List<BlockInput> blocks = toBlocks("![alt](http://x/y.png) some caption");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.PARAGRAPH);
    assertThat(blocks.get(0).content()).isEqualTo("![alt](http://x/y.png) some caption");
  }

  @Test
  void doesNotHangOnHalfTypedImage() {
    List<BlockInput> blocks = toBlocks("![alt");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.PARAGRAPH);
  }

  @Test
  void convertsHeadings() {
    List<BlockInput> blocks = toBlocks("# Title\n## Section\n### Subsection");
    assertThat(blocks)
        .containsExactly(
            new BlockInput(PostBlockType.H1, "Title"),
            new BlockInput(PostBlockType.H2, "Section"),
            new BlockInput(PostBlockType.H3, "Subsection"));
  }

  @Test
  void convertsMultiLineParagraphs() {
    List<BlockInput> blocks = toBlocks("Line one\nLine two\n\nNew paragraph");
    assertThat(blocks)
        .containsExactly(
            new BlockInput(PostBlockType.PARAGRAPH, "Line one\nLine two"),
            new BlockInput(PostBlockType.PARAGRAPH, "New paragraph"));
  }

  @Test
  void convertsBlockquote() {
    assertThat(toBlocks("> wisdom")).containsExactly(new BlockInput(PostBlockType.QUOTE, "wisdom"));
  }

  @Test
  void convertsDivider() {
    assertThat(toBlocks("para\n\n---\n\nafter"))
        .containsExactly(
            new BlockInput(PostBlockType.PARAGRAPH, "para"),
            new BlockInput(PostBlockType.DIVIDER, null),
            new BlockInput(PostBlockType.PARAGRAPH, "after"));
  }

  @Test
  void convertsImageAltAndUrl() {
    List<BlockInput> blocks = toBlocks("![cover](https://cdn/x.png)");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.IMAGE);
    assertThat(blocks.get(0).content())
        .isEqualTo("{\"url\":\"https://cdn/x.png\",\"alt\":\"cover\"}");
  }

  @Test
  void roundTripsImageCaptionViaMarkdownTitle() {
    String md = "![«wide» 사진](https://cdn/x.png \"어느 봄날, 도쿄\")";
    List<BlockInput> blocks = toBlocks(md);
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.IMAGE);
    assertThat(blocks.get(0).content())
        .isEqualTo(
            "{\"url\":\"https://cdn/x.png\",\"alt\":\"사진\",\"width\":\"wide\","
                + "\"caption\":\"어느 봄날, 도쿄\"}");
    assertThat(roundTrip(blocks)).isEqualTo(md);
  }

  @Test
  void serializesCaptionWithQuoteUsingSingleQuote() {
    // 캡션 안의 " 는 image title 을 깨므로 ' 로 치환해 직렬화한다(방어적 경로).
    String content = "{\"url\":\"https://cdn/x.png\",\"alt\":\"\",\"caption\":\"그가 \\\"진심\\\"\"}";
    String md = roundTrip(List.of(new BlockInput(PostBlockType.IMAGE, content)));
    assertThat(md).isEqualTo("![](https://cdn/x.png \"그가 '진심'\")");
  }

  @Test
  void plainImageHasNoCaption() {
    List<BlockInput> blocks = toBlocks("![alt](https://cdn/x.png)");
    assertThat(blocks.get(0).content()).doesNotContain("caption");
    assertThat(roundTrip(blocks)).isEqualTo("![alt](https://cdn/x.png)");
  }

  @Test
  void groupsBulletList() {
    List<BlockInput> blocks = toBlocks("- one\n- two\n- three");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.LIST_BULLET);
    // New format = raw markdown (nesting-capable), not a JSON array.
    assertThat(blocks.get(0).content()).isEqualTo("- one\n- two\n- three");
  }

  @Test
  void groupsNumberedList() {
    List<BlockInput> blocks = toBlocks("1. first\n2. second");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.LIST_NUMBERED);
    assertThat(blocks.get(0).content()).isEqualTo("1. first\n2. second");
  }

  @Test
  void standaloneVideoUrlBecomesEmbed() {
    assertThat(toBlocks("https://youtu.be/dQw4w9WgXcQ"))
        .containsExactly(new BlockInput(PostBlockType.EMBED, "https://youtu.be/dQw4w9WgXcQ"));
    assertThat(toBlocks("<https://www.youtube.com/watch?v=dQw4w9WgXcQ>").get(0).type())
        .isEqualTo(PostBlockType.EMBED);
    assertThat(toBlocks("[clip](https://vimeo.com/123456789)").get(0).type())
        .isEqualTo(PostBlockType.EMBED);
  }

  @Test
  void standaloneNonVideoUrlBecomesEmbedCardToo() {
    assertThat(toBlocks("https://example.com/article").get(0).type())
        .isEqualTo(PostBlockType.EMBED);
  }

  @Test
  void urlWithSurroundingTextStaysParagraph() {
    assertThat(toBlocks("see https://example.com/article for more").get(0).type())
        .isEqualTo(PostBlockType.PARAGRAPH);
  }

  @Test
  void splitsVideoUrlOutOfSurroundingText() {
    List<BlockInput> blocks = toBlocks("intro line\nhttps://youtu.be/dQw4w9WgXcQ\noutro line");
    assertThat(blocks.stream().map(BlockInput::type))
        .containsExactly(PostBlockType.PARAGRAPH, PostBlockType.EMBED, PostBlockType.PARAGRAPH);
  }

  @Test
  void roundTripsEmbedBlock() {
    List<BlockInput> blocks = toBlocks("https://youtu.be/dQw4w9WgXcQ");
    assertThat(toBlocks(roundTrip(blocks))).isEqualTo(blocks);
  }

  @Test
  void coalescesMultiLineBlockquoteIntoOneBlock() {
    List<BlockInput> blocks = toBlocks("> first line\n> second line");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.QUOTE);
    assertThat(blocks.get(0).content()).isEqualTo("first line\nsecond line");
    // The serializer prefixes every line, so it parses back to ONE quote, not N boxes.
    assertThat(toBlocks(roundTrip(blocks))).isEqualTo(blocks);
  }

  @Test
  void parsesFencedCodeWithLang() {
    assertThat(toBlocks("```js\nconst x = 1;\n```"))
        .containsExactly(
            new BlockInput(PostBlockType.CODE, "{\"lang\":\"js\",\"code\":\"const x = 1;\"}"));
  }

  @Test
  void keepsBlankLineInsideCode() {
    List<BlockInput> blocks = toBlocks("```\nconst a = 1;\n\nconst b = 2;\n```");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.CODE);
    assertThat(blocks.get(0).content()).contains("const a = 1;\\n\\nconst b = 2;");
  }

  @Test
  void doesNotTreatMarkdownLikeLinesInsideCodeAsBlocks() {
    List<BlockInput> blocks = toBlocks("```\n- not a list\n# not a heading\n```");
    assertThat(blocks).hasSize(1);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.CODE);
  }

  @Test
  void separatesParagraphFromCodeFenceWithoutBlankLine() {
    List<BlockInput> blocks = toBlocks("intro text\n```\ncode\n```");
    assertThat(blocks.stream().map(BlockInput::type))
        .containsExactly(PostBlockType.PARAGRAPH, PostBlockType.CODE);
    assertThat(blocks.get(0).content()).isEqualTo("intro text");
  }

  @Test
  void roundTripsCodeBlock() {
    List<BlockInput> blocks = toBlocks("```ts\nlet n = 0;\n\nn += 1;\n```");
    assertThat(toBlocks(roundTrip(blocks))).isEqualTo(blocks);
  }

  @Test
  void parsesWideMarkedImageAndRoundTrips() {
    List<BlockInput> blocks = toBlocks("![«wide» My photo](https://x/a.png)");
    assertThat(blocks)
        .containsExactly(
            new BlockInput(
                PostBlockType.IMAGE,
                "{\"url\":\"https://x/a.png\",\"alt\":\"My photo\",\"width\":\"wide\"}"));
    assertThat(toBlocks(roundTrip(blocks))).isEqualTo(blocks);
  }

  @Test
  void plainImageHasNoWidth() {
    List<BlockInput> blocks = toBlocks("![plain](https://x/b.png)");
    assertThat(blocks.get(0).content())
        .isEqualTo("{\"url\":\"https://x/b.png\",\"alt\":\"plain\"}");
  }

  @Test
  void sideBySideHalfImagesSplitIntoTwoBlocks() {
    List<BlockInput> blocks = toBlocks("![«half» a](https://x/1.png)![«half» b](https://x/2.png)");
    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0).type()).isEqualTo(PostBlockType.IMAGE);
    assertThat(blocks.get(1).type()).isEqualTo(PostBlockType.IMAGE);
  }

  @Test
  void parsesGfmTableIntoRawMarkdownBlock() {
    String md = "| a | b |\n| --- | --- |\n| 1 | 2 |";
    assertThat(toBlocks(md)).containsExactly(new BlockInput(PostBlockType.TABLE, md));
  }

  @Test
  void singlePipeLineIsNotATable() {
    assertThat(toBlocks("a | b not a table").get(0).type()).isEqualTo(PostBlockType.PARAGRAPH);
  }

  @Test
  void roundTripsTableBlock() {
    List<BlockInput> blocks = toBlocks("| h1 | h2 |\n| --- | --- |\n| x | y |");
    assertThat(toBlocks(roundTrip(blocks))).isEqualTo(blocks);
  }

  @Test
  void roundTripsBasicDocument() {
    String md = "# Hello\n\nFirst paragraph.\n\n> a quote\n\n---\n\n- a\n- b";
    List<BlockInput> blocks = toBlocks(md);
    // Not byte-identical markdown, but re-parsing yields identical blocks.
    assertThat(toBlocks(roundTrip(blocks))).isEqualTo(blocks);
  }

  @Test
  void serializesImageBlock() {
    String back =
        converter.toMarkdown(
            List.of(
                new PostBlockView(null, "IMAGE", "{\"url\":\"https://x\",\"alt\":\"a\"}", null)));
    assertThat(back).isEqualTo("![a](https://x)");
  }

  @Test
  void legacyJsonListArrayStillSerializes() {
    String back =
        converter.toMarkdown(
            List.of(new PostBlockView(null, "LIST_NUMBERED", "[\"first\",\"second\"]", null)));
    assertThat(back).isEqualTo("1. first\n2. second");
  }

  @Test
  void legacyEmbedJsonStillSerializes() {
    String back =
        converter.toMarkdown(
            List.of(new PostBlockView(null, "EMBED", "{\"url\":\"https://youtu.be/abc\"}", null)));
    assertThat(back).isEqualTo("https://youtu.be/abc");
  }

  @Test
  void ctaRefIsPreservedVerbatim() {
    String back = converter.toMarkdown(List.of(new PostBlockView(null, "CTA_REF", "cta:42", null)));
    assertThat(back).isEqualTo("cta:42");
  }

  @Test
  void codeFenceGrowsPastBackticksInCode() {
    assertThat(MarkdownBlocksConverter.fenceFor("plain")).isEqualTo("```");
    assertThat(MarkdownBlocksConverter.fenceFor("a ````raw```` b")).isEqualTo("`````");
  }
}
