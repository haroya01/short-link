package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 발행글의 검색 가능한 평문(search_text)을 만든다. 제목·요약·태그와 함께 본문 블록을 사람이 읽는 자연어로 펼쳐, FULLTEXT 인덱스 한 컬럼이 본문까지 훑을
 * 수 있게 한다. 예전 검색은 title·excerpt·태그·작가 핸들만 봤고 본문은 통째로 빠져 있었다 — 여기서 본문을 채운다.
 *
 * <p>블록 payload 는 타입마다 모양이 다르다({@link MarkdownBlocksConverter} 와 같은 계약): 텍스트 계열은 그대로, IMAGE 는
 * alt·caption, LIST 는 항목 텍스트, CODE 는 코드, TABLE 은 셀 텍스트를 뽑는다. URL·마크다운 기호처럼 검색어가 될 리 없는 노이즈는
 * 버린다(EMBED URL, CTA 참조, 구분선).
 *
 * <p>작가 핸들은 다른 테이블(users.username)이라 컬럼 인덱스에 담을 수 없어 여기에 넣지 않는다 — 검색 쿼리가 별도 서브쿼리로 계속 매칭한다.
 */
public final class PostSearchTextFlattener {

  // MySQL TEXT 는 65,535 바이트. utf8mb4 최악(4바이트/문자)에도 안전하도록 문자 수를 넉넉히 자른다. FULLTEXT 는
  // 앞부분 토큰만으로도 충분히 잡히고, 초장문 본문이 인덱스를 비대하게 만드는 것을 막는다.
  static final int MAX_SEARCH_TEXT_CHARS = 12_000;

  private final JsonMapper json;

  public PostSearchTextFlattener(JsonMapper json) {
    this.json = json;
  }

  /**
   * 제목·요약·태그·본문 블록을 하나의 검색 평문으로 합친다. 각 조각은 공백 한 칸으로 잇고, 앞뒤 공백을 정리한 뒤 길이를 캡한다. 매칭될 게 아무것도 없으면 빈
   * 문자열(널 아님)을 돌려준다 — 인덱스는 빈 문자열도 문제없이 담는다.
   */
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

  /** 한 블록의 검색용 자연어 텍스트. 텍스트가 없는 타입(구분선·CTA·임베드)은 빈 문자열. */
  private String blockText(PostBlockType type, String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    return switch (type) {
        // 순수 텍스트 블록 — 그대로.
      case PARAGRAPH, H1, H2, H3, QUOTE -> content;
        // TABLE = 원본 GFM 마크다운. 셀 텍스트가 파이프/대시에 섞여 있지만 ngram 토큰화가 알아서 걸러 잡는다.
      case TABLE -> content;
        // LIST = 신형은 원본 마크다운, 구형은 JSON 문자열 배열. 둘 다 항목 텍스트를 뽑는다.
      case LIST_BULLET, LIST_NUMBERED -> listText(content);
      case IMAGE -> imageText(content);
      case CODE -> codeText(content);
        // EMBED(URL만), CTA_REF(참조 id), DIVIDER(널) — 검색어가 될 자연어 없음.
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
    // 신형(원본 마크다운) — 리스트 마커(-, *, 1.)는 ngram 이 걸러내므로 원문 그대로 둔다.
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
