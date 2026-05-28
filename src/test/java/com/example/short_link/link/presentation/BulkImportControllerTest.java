package com.example.short_link.link.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.BulkImportResult;
import com.example.short_link.link.application.dto.BulkImportRow;
import com.example.short_link.link.application.write.ImportLinksFromCsvUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = BulkImportController.class)
class BulkImportControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private ImportLinksFromCsvUseCase useCase;
  @MockitoBean private ShortLinkUrlBuilder urlBuilder;

  private static final long USER_ID = 7L;

  @Test
  void anonymousIs401() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "x.csv", "text/csv", "url\nhttp://x".getBytes());
    mvc.perform(multipart("/api/v1/links/bulk").file(file)).andExpect(status().isUnauthorized());
  }

  @Test
  void emptyFileReturns400() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

    mvc.perform(
            multipart("/api/v1/links/bulk")
                .file(file)
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("empty file"));
  }

  @Test
  void importSucceedsAndReturnsCsvWithHeaders() throws Exception {
    when(urlBuilder.build(new ShortCode("ok00001"))).thenReturn("http://localhost:8080/ok00001");
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
            multipart("/api/v1/links/bulk")
                .file(file)
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Bulk-Ok", "1"))
        .andExpect(header().string("X-Bulk-Failed", "1"))
        .andExpect(
            content()
                .string(
                    Matchers.containsString(
                        "url,custom_code,expires_at,short_code,short_url,error")))
        .andExpect(content().string(Matchers.containsString("https://example.com")))
        .andExpect(content().string(Matchers.containsString("url contains comma")));
  }
}
