package com.example.short_link.user.presentation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import com.example.short_link.user.application.read.BlockQueryService;
import com.example.short_link.user.application.read.BlockedUserView;
import com.example.short_link.user.application.write.BlockUseCase;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = BlockController.class)
@Import(UserExceptionHandler.class)
class BlockControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private BlockUseCase blockUseCase;
  @MockitoBean private BlockQueryService blockQueryService;

  private static final long USER_ID = 9L;

  @Test
  void blockReturns204() throws Exception {
    mvc.perform(
            put("/api/v1/users/bob/block").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(blockUseCase).block(USER_ID, "bob");
  }

  @Test
  void unblockReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/users/bob/block")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(blockUseCase).unblock(USER_ID, "bob");
  }

  @Test
  void myBlocksReturnsList() throws Exception {
    when(blockQueryService.myBlocks(USER_ID))
        .thenReturn(List.of(new BlockedUserView(2L, "bob", "https://cdn/x.jpg")));

    mvc.perform(
            get("/api/v1/users/me/blocks").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(2))
        .andExpect(jsonPath("$[0].username").value("bob"));
  }

  @Test
  void blockingYourselfIs400() throws Exception {
    doThrow(new UserException(UserErrorCode.CANNOT_BLOCK_SELF))
        .when(blockUseCase)
        .block(USER_ID, "me");

    mvc.perform(
            put("/api/v1/users/me/block").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));
  }

  @Test
  void anonymousBlockIs401() throws Exception {
    mvc.perform(put("/api/v1/users/bob/block")).andExpect(status().isUnauthorized());
  }
}
