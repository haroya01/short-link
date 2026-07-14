package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임시저장(본문 교체) ↔ 발행 스냅샷(리비전) ↔ 롤백(복원)의 전체 수명주기를 실제 스택(DB·컨버터)으로 고정한다 — iOS/웹 에디터가 기대는 계약이 코드 리딩이 아니라
 * 테스트로 서 있게. 핵심 불변식:
 *
 * <ul>
 *   <li>임시저장(PUT /markdown)은 리비전을 만들지 않는다 — 롤백 대상은 "발행 시점"뿐이다.
 *   <li>공개되는 순간(발행·재게시)마다 스냅샷 한 장 — 복원은 그 시점의 본문+가벼운 메타로 되돌린다.
 *   <li>복원은 slug·status 를 건드리지 않고, 그 자체로 새 리비전을 만들지 않는다.
 *   <li>복합 마크다운은 PUT→GET 왕복의 고정점이다(서버 정규화 멱등) — 저장할 때마다 본문이 변형되면 에디터 자동저장이 무한 더티 루프에 빠진다.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostDraftRevisionLifecycleTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  /** 에디터가 만드는 블록 종류를 한 몸에 담은 복합 본문 — 왕복·스냅샷·복원의 스트레스 픽스처. */
  private static final String COMPLEX_MARKDOWN =
      """
      # 제목 하나

      본문 **볼드**와 *이탤릭*, `코드`, [링크](https://example.com/a) 가 섞인 문단.

      ## 목록과 중첩

      - 하나
      - 둘
        - 둘의 하나
      1. 첫
      2. 둘

      > 인용 첫 줄
      > 인용 둘째 줄

      ```swift
      let x = "hello"
      ```

      ---

      ![«wide» «1200x800» 히어로](https://cdn.example/hero.png "커버 설명")

      | 이름 | 값 |
      | --- | ---: |
      | 파이프 \\| 이스케이프 | 42 |

      https://youtu.be/dQw4w9WgXcQ
      """;

  private String token(String seed) {
    UserEntity user = userRepository.save(new UserEntity(seed + "@x.com", "google", seed));
    return jwt.createAccessToken(user.getId(), "USER");
  }

  private long createDraft(String token, String slug, String title) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"slug\":\""
                            + slug
                            + "\",\"title\":\""
                            + title
                            + "\",\"languageTag\":\"ko\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("id").asLong();
  }

  /** PUT /markdown — 응답의 정규화(canonical) 본문을 돌려준다(이후 비교의 기준). */
  private String putMarkdown(String token, long postId, String markdown) throws Exception {
    String body =
        mvc.perform(
                put("/api/v1/posts/" + postId + "/markdown")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(java.util.Map.of("markdown", markdown))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("markdown").asText();
  }

  private String getMarkdown(String token, long postId) throws Exception {
    String body =
        mvc.perform(
                get("/api/v1/posts/" + postId + "/markdown")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("markdown").asText();
  }

  private JsonNode revisions(String token, long postId) throws Exception {
    String body =
        mvc.perform(
                get("/api/v1/posts/" + postId + "/revisions")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body);
  }

  private void act(String token, long postId, String action) throws Exception {
    mvc.perform(
            post("/api/v1/posts/" + postId + "/" + action)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  // MARK: 임시저장 = 본문 교체(리비전 없음)

  @Test
  void autosaveReplacesBodyWithoutCreatingRevisions() throws Exception {
    String token = token("g-lc-autosave");
    long id = createDraft(token, "lc-autosave", "자동저장 계약");

    putMarkdown(token, id, "첫 저장");
    putMarkdown(token, id, "둘째 저장");
    String canonical = putMarkdown(token, id, "# 셋째\n\n마지막 저장");

    assertThat(getMarkdown(token, id)).isEqualTo(canonical);
    assertThat(revisions(token, id)).isEmpty();
  }

  @Test
  void complexMarkdownIsAFixedPointOfSaveThenLoad() throws Exception {
    String token = token("g-lc-fixed");
    long id = createDraft(token, "lc-fixed-point", "왕복 고정점");

    String canonical = putMarkdown(token, id, COMPLEX_MARKDOWN);
    assertThat(getMarkdown(token, id)).isEqualTo(canonical);

    // 정규화 본문을 다시 저장해도 그대로여야 한다 — 저장할 때마다 변형되면 자동저장이 더티 루프에 빠진다.
    String second = putMarkdown(token, id, canonical);
    assertThat(second).isEqualTo(canonical);
    assertThat(getMarkdown(token, id)).isEqualTo(canonical);
  }

  // MARK: 발행 = 스냅샷 한 장, 편집은 스냅샷을 더 만들지 않는다

  @Test
  void publishCapturesOneRevisionAndLaterEditsDoNot() throws Exception {
    String token = token("g-lc-pub");
    long id = createDraft(token, "lc-publish", "발행 스냅샷");
    putMarkdown(token, id, COMPLEX_MARKDOWN);

    act(token, id, "publish");
    JsonNode afterPublish = revisions(token, id);
    assertThat(afterPublish).hasSize(1);
    assertThat(afterPublish.get(0).get("versionNumber").asInt()).isEqualTo(1);
    assertThat(afterPublish.get(0).get("titleSnapshot").asText()).isEqualTo("발행 스냅샷");

    putMarkdown(token, id, "발행 뒤 고친 본문");
    assertThat(revisions(token, id)).hasSize(1);
  }

  // MARK: 롤백 — 발행 시점 본문·제목으로, slug·status 는 그대로, 새 리비전 없음

  @Test
  void restoreRollsBackBodyAndTitleButKeepsSlugStatusAndRevisions() throws Exception {
    String token = token("g-lc-restore");
    long id = createDraft(token, "lc-restore", "원래 제목");
    String canonicalV1 = putMarkdown(token, id, COMPLEX_MARKDOWN);
    act(token, id, "publish");

    // 발행 뒤 제목·본문을 다 바꾼다.
    mvc.perform(
            patch("/api/v1/posts/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"바뀐 제목\"}"))
        .andExpect(status().isOk());
    putMarkdown(token, id, "완전히 다른 본문");

    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/1/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // 본문·제목이 발행 시점으로 되돌아왔다.
    assertThat(getMarkdown(token, id)).isEqualTo(canonicalV1);
    String view =
        mvc.perform(get("/api/v1/posts/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode post = JSON.readTree(view);
    assertThat(post.get("title").asText()).isEqualTo("원래 제목");
    assertThat(post.get("slug").asText()).isEqualTo("lc-restore");
    assertThat(post.get("status").asText()).isEqualTo("PUBLISHED");

    // 복원 자체는 스냅샷을 만들지 않는다.
    assertThat(revisions(token, id)).hasSize(1);
  }

  // MARK: 재게시도 공개되는 순간 — 스냅샷 한 장(비공개 동안의 편집이 롤백 대상에 남게)

  @Test
  void republishCapturesRevisionSoUnpublishedEditsAreRestorable() throws Exception {
    String token = token("g-lc-repub");
    long id = createDraft(token, "lc-republish", "재게시 스냅샷");
    putMarkdown(token, id, "첫 공개 본문");
    act(token, id, "publish");
    assertThat(revisions(token, id)).hasSize(1);

    act(token, id, "unpublish");
    String canonicalV2 = putMarkdown(token, id, "# 비공개 동안 고친 본문\n\n재게시로 다시 공개된다.");
    act(token, id, "republish");

    JsonNode revs = revisions(token, id);
    assertThat(revs).hasSize(2);

    // 다시 첫 본문으로 굴려도(v1), v2 로 앞으로도 굴릴 수 있다 — 양방향 롤백.
    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/1/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(getMarkdown(token, id)).isEqualTo("첫 공개 본문");

    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/2/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(getMarkdown(token, id)).isEqualTo(canonicalV2);
  }

  // MARK: 남의 글·없는 버전은 굴릴 수 없다

  @Test
  void restoreIsGatedByOwnershipAndVersionExistence() throws Exception {
    String owner = token("g-lc-gate-owner");
    long id = createDraft(owner, "lc-gate", "게이트");
    putMarkdown(owner, id, "본문");
    act(owner, id, "publish");

    String stranger = token("g-lc-gate-stranger");
    // 남의 글 = 403(소유권 게이트가 존재를 숨기지 않는 계약) · 없는 버전 = 404.
    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/1/restore")
                .header("Authorization", "Bearer " + stranger))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/99/restore")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isNotFound());
  }
}
