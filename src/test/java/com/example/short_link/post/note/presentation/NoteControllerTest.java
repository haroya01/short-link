package com.example.short_link.post.note.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  private Long signUp(String email, String oauthId) {
    return userRepository.save(new UserEntity(email, "google", oauthId)).getId();
  }

  /** 실제 액세스 토큰 헤더 — 기존 E2E 들과 같은 인증 경로(JwtAuthenticationFilter)를 탄다. */
  private String bearer(Long userId) {
    return "Bearer " + jwt.createAccessToken(userId, "USER");
  }

  @Test
  void createThenFeedShowsNoteWithAuthor() throws Exception {
    Long author = signUp("note-a@example.com", "g-note-1");

    mvc.perform(
            post("/api/v1/notes")
                .header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"첫 노트\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.body").value("첫 노트"))
        .andExpect(jsonPath("$.author.id").value(author));

    mvc.perform(get("/api/v1/public/notes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].body").value("첫 노트"))
        .andExpect(jsonPath("$.items[0].likeCount").value(0));
  }

  @Test
  void blankOrOversizedBodyRejected() throws Exception {
    Long author = signUp("note-b@example.com", "g-note-2");

    mvc.perform(
            post("/api/v1/notes")
                .header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"   \"}"))
        .andExpect(status().isBadRequest());

    String tooLong = "글".repeat(501);
    mvc.perform(
            post("/api/v1/notes")
                .header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOTE_BODY_TOO_LONG"));
  }

  @Test
  void likeIsIdempotentAndCounted() throws Exception {
    Long author = signUp("note-c@example.com", "g-note-3");
    Long fan = signUp("note-d@example.com", "g-note-4");
    String created =
        mvc.perform(
                post("/api/v1/notes")
                    .header("Authorization", bearer(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"좋아요 대상\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long noteId = Long.parseLong(created.replaceFirst("^\\{\"id\":(\\d+).*$", "$1"));

    mvc.perform(put("/api/v1/notes/" + noteId + "/like").header("Authorization", bearer(fan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(1));
    mvc.perform(put("/api/v1/notes/" + noteId + "/like").header("Authorization", bearer(fan)))
        .andExpect(jsonPath("$.likeCount").value(1));

    mvc.perform(
            get("/api/v1/notes/like-status")
                .param("ids", String.valueOf(noteId))
                .header("Authorization", bearer(fan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likedIds[0]").value(noteId));

    mvc.perform(delete("/api/v1/notes/" + noteId + "/like").header("Authorization", bearer(fan)))
        .andExpect(jsonPath("$.likeCount").value(0));
  }

  @Test
  void onlyOwnerCanDelete() throws Exception {
    Long author = signUp("note-e@example.com", "g-note-5");
    Long stranger = signUp("note-f@example.com", "g-note-6");
    String created =
        mvc.perform(
                post("/api/v1/notes")
                    .header("Authorization", bearer(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"내 노트\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long noteId = Long.parseLong(created.replaceFirst("^\\{\"id\":(\\d+).*$", "$1"));

    mvc.perform(delete("/api/v1/notes/" + noteId).header("Authorization", bearer(stranger)))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/notes/" + noteId).header("Authorization", bearer(author)))
        .andExpect(status().isNoContent());
  }
}
