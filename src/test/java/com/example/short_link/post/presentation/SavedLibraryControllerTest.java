package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.BookmarkFolderView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.SavedLibraryQueryService;
import com.example.short_link.post.application.read.SavedView;
import com.example.short_link.post.application.write.BookmarkFolderUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = SavedLibraryController.class)
class SavedLibraryControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SavedLibraryQueryService savedLibraryQueryService;
  @MockitoBean private BookmarkFolderUseCase bookmarkFolderUseCase;

  private static final long USER_ID = 7L;

  private static String hdr() {
    return WebMvcSecurityTestConfig.USER_ID_HEADER;
  }

  @Test
  void savedReturnsFlatFeedCardsWithFolderId() throws Exception {
    when(savedLibraryQueryService.saved(eq(USER_ID)))
        .thenReturn(
            List.of(
                new SavedView(
                    42L,
                    new PublicAuthorView(100L, "alice", "bio", null),
                    "my-post",
                    "My Post",
                    "excerpt",
                    null,
                    "ko",
                    List.of("tag"),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    3L,
                    5L,
                    99L)));

    mvc.perform(get("/api/v1/me/saved").header(hdr(), USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(42))
        .andExpect(jsonPath("$[0].author.username").value("alice"))
        .andExpect(jsonPath("$[0].folderId").value(99));
  }

  @Test
  void savedAnonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/me/saved")).andExpect(status().isUnauthorized());
  }

  @Test
  void foldersReturnsList() throws Exception {
    when(savedLibraryQueryService.folders(eq(USER_ID)))
        .thenReturn(List.of(new BookmarkFolderView(1L, "나중에", 2L)));

    mvc.perform(get("/api/v1/bookmarks/folders").header(hdr(), USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("나중에"))
        .andExpect(jsonPath("$[0].count").value(2));
  }

  @Test
  void createFolderReturns201() throws Exception {
    when(bookmarkFolderUseCase.create(eq(USER_ID), eq("새폴더")))
        .thenReturn(new BookmarkFolderView(5L, "새폴더", 0L));

    mvc.perform(
            post("/api/v1/bookmarks/folders")
                .header(hdr(), USER_ID)
                .contentType("application/json")
                .content("{\"name\":\"새폴더\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(5));
  }

  @Test
  void createFolderRejectsBlankName() throws Exception {
    mvc.perform(
            post("/api/v1/bookmarks/folders")
                .header(hdr(), USER_ID)
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void renameFolderReturnsView() throws Exception {
    when(bookmarkFolderUseCase.rename(eq(USER_ID), eq(5L), eq("바뀐이름")))
        .thenReturn(new BookmarkFolderView(5L, "바뀐이름", 1L));

    mvc.perform(
            patch("/api/v1/bookmarks/folders/5")
                .header(hdr(), USER_ID)
                .contentType("application/json")
                .content("{\"name\":\"바뀐이름\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("바뀐이름"));
  }

  @Test
  void deleteFolderReturns204() throws Exception {
    mvc.perform(delete("/api/v1/bookmarks/folders/5").header(hdr(), USER_ID))
        .andExpect(status().isNoContent());
    verify(bookmarkFolderUseCase).delete(USER_ID, 5L);
  }

  @Test
  void moveBookmarkReturns204() throws Exception {
    mvc.perform(
            put("/api/v1/me/saved/42/folder")
                .header(hdr(), USER_ID)
                .contentType("application/json")
                .content("{\"folderId\":5}"))
        .andExpect(status().isNoContent());
    verify(bookmarkFolderUseCase).moveBookmark(USER_ID, 42L, 5L);
  }

  @Test
  void moveBookmarkAcceptsNullFolderToUnfile() throws Exception {
    mvc.perform(
            put("/api/v1/me/saved/42/folder")
                .header(hdr(), USER_ID)
                .contentType("application/json")
                .content("{\"folderId\":null}"))
        .andExpect(status().isNoContent());
    verify(bookmarkFolderUseCase).moveBookmark(USER_ID, 42L, null);
  }
}
