package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 본문과 메타데이터를 FULLTEXT용 평문으로 펼친다. URL·CTA 참조·구분선은 제외한다. 작성자 핸들은 다른 테이블에 있으므로 검색 쿼리가 별도로 매칭한다. */
public final class PostSearchTextFlattener {

  // MySQL TEXT의 65,535바이트 한도를 utf8mb4(문자당 최대 4바이트)에서도 넘지 않도록 제한한다.
  static final int MAX_SEARCH_TEXT_CHARS = 12_000;

  private final JsonMapper json;

  public PostSearchTextFlattener(JsonMapper json) {
    this.json = json;
  }

  /** 검색할 텍스트가 없으면 null 대신 빈 문자열을 반환한다. */
  public String flatten(
      String title, String excerpt, List<String> tags, List<PostBlockEntity> blocks) {
    StringBuilder sb = new StringBuilder();
    append(sb, title);
    append(sb, excerpt);
    if (tags != null) {
      for (String tag : tags) {
        append(sb, tag);
      }
    }
    if (blocks != null) {
      for (PostBlockEntity block : blocks) {
        append(sb, blockText(block.getType(), block.getContent()));
      }
    }
    String flattened = sb.toString().strip();
    if (flattened.length() > MAX_SEARCH_TEXT_CHARS) {
      flattened = flattened.substring(0, MAX_SEARCH_TEXT_CHARS).strip();
    }
    return flattened;
  }

  private String blockText(PostBlockType type, String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    return switch (type) {
      case PARAGRAPH, H1, H2, H3, QUOTE -> content;
        // TABLE의 파이프·대시는 ngram 토큰화가 처리하므로 마크다운 원문을 사용한다.
      case TABLE -> content;
        // LIST는 마크다운과 구형 JSON 문자열 배열을 모두 지원한다.
      case LIST_BULLET, LIST_NUMBERED -> listText(content);
      case IMAGE -> imageText(content);
      case CODE -> codeText(content);
      case EMBED, CTA_REF, DIVIDER -> "";
    };
  }

  private String listText(String content) {
    JsonNode node = readTreeOrNull(content);
    if (node != null && node.isArray()) {
      StringBuilder sb = new StringBuilder();
      for (JsonNode item : node) {
        append(sb, item.isString() ? item.stringValue() : item.asString());
      }
      return sb.toString();
    }
    // 마크다운 리스트 마커는 ngram 토큰화가 처리한다.
    return content;
  }

  private String imageText(String content) {
    JsonNode node = readTreeOrNull(content);
    if (node == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (node.path("alt").isString()) {
      append(sb, node.path("alt").stringValue());
    }
    if (node.path("caption").isString()) {
      append(sb, node.path("caption").stringValue());
    }
    return sb.toString();
  }

  private String codeText(String content) {
    JsonNode node = readTreeOrNull(content);
    if (node != null && node.path("code").isString()) {
      return node.path("code").stringValue();
    }
    // 구형/평문 CODE 는 content 자체가 코드.
    return content;
  }

  private JsonNode readTreeOrNull(String content) {
    try {
      return json.readTree(content);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static void append(StringBuilder sb, String piece) {
    if (piece == null || piece.isBlank()) {
      return;
    }
    if (sb.length() > 0) {
      sb.append(' ');
    }
    sb.append(piece.strip());
  }
}
