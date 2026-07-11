package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PostSearchTextFlattenerTest {

  private final PostSearchTextFlattener flattener =
      new PostSearchTextFlattener(JsonMapper.builder().build());

  private static PostBlockEntity block(PostBlockType type, String content) {
    return new PostBlockEntity(1L, type, content, 0);
  }

  @Test
  void composesTitleExcerptAndTags() {
    String text = flattener.flatten("헥사고날 아키텍처", "포트와 어댑터", List.of("spring", "설계"), List.of());
    assertThat(text).isEqualTo("헥사고날 아키텍처 포트와 어댑터 spring 설계");
  }

  @Test
  void includesPlainTextBlocks() {
    String text =
        flattener.flatten(
            "제목",
            null,
            List.of(),
            List.of(
                block(PostBlockType.H2, "본문 소제목"),
                block(PostBlockType.PARAGRAPH, "리다이렉트 성능에 대한 문단"),
                block(PostBlockType.QUOTE, "인용된 문장")));
    assertThat(text).contains("본문 소제목").contains("리다이렉트 성능에 대한 문단").contains("인용된 문장");
  }

  @Test
  void extractsImageAltAndCaptionButNotUrl() {
    String text =
        flattener.flatten(
            null,
            null,
            List.of(),
            List.of(
                block(
                    PostBlockType.IMAGE,
                    "{\"url\":\"https://cdn/x.png\",\"alt\":\"아키텍처 다이어그램\",\"caption\":\"그림 1\"}")));
    assertThat(text)
        .contains("아키텍처 다이어그램")
        .contains("그림 1")
        .doesNotContain("cdn")
        .doesNotContain("png");
  }

  @Test
  void extractsCodeBodyFromJson() {
    String text =
        flattener.flatten(
            null,
            null,
            List.of(),
            List.of(
                block(PostBlockType.CODE, "{\"lang\":\"java\",\"code\":\"System.out.println\"}")));
    assertThat(text).contains("System.out.println");
  }

  @Test
  void fallsBackToRawContentForPlainCode() {
    // 구형/평문 CODE 블록은 content 자체가 코드.
    String text =
        flattener.flatten(
            null, null, List.of(), List.of(block(PostBlockType.CODE, "plain code body")));
    assertThat(text).isEqualTo("plain code body");
  }

  @Test
  void extractsLegacyJsonArrayListItems() {
    String text =
        flattener.flatten(
            null,
            null,
            List.of(),
            List.of(block(PostBlockType.LIST_BULLET, "[\"첫 항목\",\"둘째 항목\"]")));
    assertThat(text).contains("첫 항목").contains("둘째 항목");
  }

  @Test
  void keepsRawMarkdownListItems() {
    // 신형 리스트는 원본 마크다운 — 항목 텍스트가 그대로 들어간다.
    String text =
        flattener.flatten(
            null, null, List.of(), List.of(block(PostBlockType.LIST_NUMBERED, "1. 사과\n2. 배")));
    assertThat(text).contains("사과").contains("배");
  }

  @Test
  void includesTableCellText() {
    String text =
        flattener.flatten(
            null,
            null,
            List.of(),
            List.of(block(PostBlockType.TABLE, "| 이름 | 값 |\n| --- | --- |\n| 알파 | 1 |")));
    assertThat(text).contains("이름").contains("알파");
  }

  @Test
  void skipsNonTextBlocks() {
    String text =
        flattener.flatten(
            "제목",
            null,
            List.of(),
            List.of(
                block(PostBlockType.DIVIDER, null),
                block(PostBlockType.EMBED, "https://youtube.com/watch?v=x"),
                block(PostBlockType.CTA_REF, "{\"ctaId\":9}")));
    // 자연어 없는 블록은 무시 — 제목만 남는다(임베드 URL·CTA 참조 노이즈 제외).
    assertThat(text).isEqualTo("제목");
  }

  @Test
  void handlesMalformedJsonGracefully() {
    // 깨진 JSON 은 파싱 실패해도 예외 없이(IMAGE→빈 문자열, LIST→원문 그대로).
    String image =
        flattener.flatten(null, null, List.of(), List.of(block(PostBlockType.IMAGE, "{not json")));
    assertThat(image).isEmpty();
  }

  @Test
  void returnsEmptyStringNotNullWhenNothingToIndex() {
    assertThat(flattener.flatten(null, null, List.of(), List.of())).isEmpty();
    assertThat(flattener.flatten("  ", "", List.of(""), List.of())).isEmpty();
  }

  @Test
  void capsVeryLongText() {
    String huge = "가".repeat(20_000);
    String text =
        flattener.flatten("t", null, List.of(), List.of(block(PostBlockType.PARAGRAPH, huge)));
    assertThat(text.length()).isLessThanOrEqualTo(PostSearchTextFlattener.MAX_SEARCH_TEXT_CHARS);
  }

  @Test
  void toleratesNullTagsAndBlocks() {
    assertThat(flattener.flatten("t", "e", null, null)).isEqualTo("t e");
  }
}
