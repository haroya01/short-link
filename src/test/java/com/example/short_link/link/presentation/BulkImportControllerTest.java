package com.example.short_link.link.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.dto.BulkImportResult;
import com.example.short_link.link.application.dto.BulkImportRow;
import com.example.short_link.link.application.write.ImportLinksFromCsvUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BulkImportControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private ImportLinksFromCsvUseCase useCase;

  @Test
  void anonymousIs401() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "x.csv", "text/csv", "url\nhttp://x".getBytes());
    mvc.perform(multipart("/api/v1/links/bulk").file(file)).andExpect(status().isUnauthorized());
  }

  @Test
  void emptyFileReturns400() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("e@x.com", "google", "g-be"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

    mvc.perform(
            multipart("/api/v1/links/bulk").file(file).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("empty file"));
  }

  @Test
  void importSucceedsAndReturnsCsvWithHeaders() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-bu"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(useCase.execute(any()))
        .thenReturn(
            new BulkImportResult(
                1,
                1,
                List.of(
                    new BulkImportRow(
                        "https://example.com", null, null, new ShortCode("ok00001"), null),
                    new BulkImportRow("https://bad,com", null, null, null, "url contains comma"))));
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "import.csv",
            "text/csv",
            "url\nhttps://example.com\nhttps://bad,com\n".getBytes());

    mvc.perform(
            multipart("/api/v1/links/bulk").file(file).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Bulk-Ok", "1"))
        .andExpect(header().string("X-Bulk-Failed", "1"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "url,custom_code,expires_at,short_code,short_url,error")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("https://example.com")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("url contains comma")));
  }
}
