package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PostCoverChosenMigrationTest {

  private final JsonMapper json = JsonMapper.builder().build();

  private String imageOf(String type, String content) {
    return V182__post_cover_chosen.imageOf(json, type, content);
  }

  @Test
  void readsTheUrlOfAnImageBlock() {
    assertThat(imageOf("IMAGE", "{\"url\":\"https://cdn/a.png\",\"alt\":\"\"}"))
        .isEqualTo("https://cdn/a.png");
    assertThat(imageOf("IMAGE", "{\"url\":\"\",\"alt\":\"\"}")).isNull();
    assertThat(imageOf("IMAGE", "not json")).isNull();
  }

  @Test
  void findsTheFirstMarkdownImageInsideAParagraph() {
    String pasted =
        "![«546x588» image.png](https://cdn/7/136/b2c2.png)![«558x387» image.png](https://cdn/7/136/1a75.png)"
            + "DATA_IO module은 BRAM에서 데이터를 받아온다.";
    assertThat(imageOf("PARAGRAPH", pasted)).isEqualTo("https://cdn/7/136/b2c2.png");
    assertThat(imageOf("PARAGRAPH", "앞 글 ![캡션](https://cdn/b.png \"waveform\") 뒤 글"))
        .isEqualTo("https://cdn/b.png");
    assertThat(imageOf("LIST_BULLET", "- ![](https://cdn/c.png)")).isEqualTo("https://cdn/c.png");
  }

  @Test
  void ignoresBlocksThatOnlyShowImageSyntax() {
    assertThat(imageOf("CODE", "{\"code\":\"![x](https://cdn/d.png)\"}")).isNull();
    assertThat(imageOf("PARAGRAPH", "그림이 없는 문단")).isNull();
    assertThat(imageOf("PARAGRAPH", null)).isNull();
  }
}
