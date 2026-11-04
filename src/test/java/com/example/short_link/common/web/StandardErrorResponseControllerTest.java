package com.example.short_link.common.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.write.ImportLinksFromCsvUseCase;
import com.example.short_link.link.presentation.BulkImportController;
import com.example.short_link.profile.application.oembed.OembedMetadata;
import com.example.short_link.profile.application.oembed.OembedService;
import com.example.short_link.profile.presentation.oembed.OembedController;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {OembedController.class, BulkImportController.class})
class StandardErrorResponseControllerTest {

  private static final String OEMBED = "/api/v1/public/oembed";
  private static final String BULK = "/api/v1/links/bulk";

  @Autowired private MockMvc mvc;

  @MockitoBean private OembedService oembed;
  @MockitoBean private ImportLinksFromCsvUseCase importLinks;
  @MockitoBean private ShortLinkUrlBuilder urlBuilder;

  @Test
  void aMethodTheRouteDoesNotServeIs405WithItsAllowedMethods() throws Exception {
    mvc.perform(
            post(OEMBED)
                .param("url", "https://www.youtube.com/watch?v=abc")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, 7L))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "GET"))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void aBodyTypeTheRouteDoesNotReadIs415WithTheTypesItReads() throws Exception {
    mvc.perform(
            post(BULK)
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(header().string("Accept", MediaType.MULTIPART_FORM_DATA_VALUE))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void aResponseTypeTheRouteCannotWriteIs406() throws Exception {
    when(oembed.fetch("https://www.youtube.com/watch?v=abc"))
        .thenReturn(
            new OembedMetadata(
                "youtube", "video", "T", "Author", "https://thumb", "<iframe/>", 320, 180));

    mvc.perform(
            get(OEMBED)
                .param("url", "https://www.youtube.com/watch?v=abc")
                .accept(MediaType.IMAGE_PNG))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void aMissingQueryParameterIs400NamingIt() throws Exception {
    mvc.perform(get(OEMBED))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        .andExpect(jsonPath("$.parameter").value("url"));
  }

  @Test
  void aMissingMultipartPartIs400NamingIt() throws Exception {
    mvc.perform(
            multipart(BULK)
                .file(new MockMultipartFile("other", "x.csv", "text/csv", "url".getBytes()))
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, 7L))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        .andExpect(jsonPath("$.parameter").value("file"));
  }

  @Test
  void aParameterFailingItsConstraintIs400() throws Exception {
    mvc.perform(get(OEMBED).param("url", "not a url"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
