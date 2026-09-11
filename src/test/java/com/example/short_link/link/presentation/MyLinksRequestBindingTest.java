package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.MyLinksCursor;
import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksQuery.SortDir;
import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import com.example.short_link.link.application.dto.MyLinksResult;
import com.example.short_link.link.application.read.MyLinksQueryService;
import com.example.short_link.link.domain.LinkExpiryFilter;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = MyLinksController.class)
class MyLinksRequestBindingTest {

  private static final long USER_ID = 7L;

  @Autowired private MockMvc mvc;
  @MockitoBean private MyLinksQueryService service;
  @MockitoBean private ShortLinkUrlBuilder urlBuilder;

  @BeforeEach
  void emptyResult() {
    when(service.myLinks(eq(USER_ID), any(MyLinksQuery.class)))
        .thenReturn(new MyLinksResult(List.of(), null, false));
  }

  @Test
  void bindsEveryNamedFilterAndDecodesTheCursorWithoutSwappingStringFields() throws Exception {
    Instant createdAfter = Instant.parse("2026-01-01T00:00:00Z");
    Instant createdBefore = Instant.parse("2026-09-01T00:00:00Z");
    MyLinksCursor after = new MyLinksCursor(createdAfter, 42L, 99L);

    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param("size", "7")
                .param("after", after.encode())
                .param("q", "  release  ")
                .param("tag", "  work  ")
                .param("domain", "  example.com  ")
                .param("expiry", "  expired  ")
                .param("createdAfter", "  " + createdAfter + "  ")
                .param("createdBefore", createdBefore.toString())
                .param("sort", "  click_count  ")
                .param("dir", "  ASC  "))
        .andExpect(status().isOk());

    MyLinksQuery query = submittedQuery();
    assertThat(query.size()).isEqualTo(7);
    assertThat(query.after()).isEqualTo(after);
    assertThat(query.q()).isEqualTo("release");
    assertThat(query.tag()).isEqualTo("work");
    assertThat(query.domain()).isEqualTo("example.com");
    assertThat(query.expiry()).isEqualTo(LinkExpiryFilter.EXPIRED);
    assertThat(query.createdAfter()).isEqualTo(createdAfter);
    assertThat(query.createdBefore()).isEqualTo(createdBefore);
    assertThat(query.sort()).isEqualTo(SortKey.CLICK_COUNT);
    assertThat(query.dir()).isEqualTo(SortDir.ASC);
  }

  @Test
  void omittedParametersKeepTheExistingFirstPageDefaults() throws Exception {
    mvc.perform(get("/api/v1/links/me").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());

    assertThat(submittedQuery()).isEqualTo(MyLinksQuery.builder().build());
  }

  @Test
  void emptySizeAndBlankStringsKeepTheSameDefaults() throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param("size", "")
                .param("after", "  ")
                .param("q", "  ")
                .param("tag", "  ")
                .param("domain", "  ")
                .param("expiry", "  ")
                .param("createdAfter", "  ")
                .param("createdBefore", "  ")
                .param("sort", "  ")
                .param("dir", "  "))
        .andExpect(status().isOk());

    assertThat(submittedQuery()).isEqualTo(MyLinksQuery.builder().build());
  }

  @Test
  void filtersAndCursorIgnoreSameNamedHeaders() throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .header("after", "not-base64-!!!")
                .header("q", "hidden search")
                .header("tag", "hidden tag")
                .header("domain", "hidden.example")
                .header("expiry", "tomorrow")
                .header("createdAfter", "not-a-date")
                .header("createdBefore", "not-a-date")
                .header("sort", "url")
                .header("dir", "sideways"))
        .andExpect(status().isOk());

    assertThat(submittedQuery()).isEqualTo(MyLinksQuery.builder().build());
  }

  @Test
  void formDefaultPrefixesDoNotSupplyMissingFilters() throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param("!after", "not-base64-!!!")
                .param("!q", "hidden search")
                .param("!tag", "hidden tag")
                .param("!domain", "hidden.example")
                .param("!expiry", "tomorrow")
                .param("!createdAfter", "not-a-date")
                .param("!createdBefore", "not-a-date")
                .param("!sort", "url")
                .param("!dir", "sideways"))
        .andExpect(status().isOk());

    assertThat(submittedQuery()).isEqualTo(MyLinksQuery.builder().build());
  }

  @ParameterizedTest
  @ValueSource(strings = {"q", "q[]"})
  void repeatedTextAndArrayAliasesKeepRequestParameterSemantics(String parameter) throws Exception {
    var cursor = new MyLinksCursor(Instant.parse("2026-01-01T00:00:00Z"), 42L, 99L);

    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param(parameter, "release", "work")
                .param("after[]", cursor.encode())
                .param("sort[]", "click_count")
                .param("dir[]", "asc"))
        .andExpect(status().isOk());

    MyLinksQuery query = submittedQuery();
    assertThat(query.q()).isEqualTo("release,work");
    assertThat(query.after()).isEqualTo(cursor);
    assertThat(query.sort()).isEqualTo(SortKey.CLICK_COUNT);
    assertThat(query.dir()).isEqualTo(SortDir.ASC);
  }

  @Test
  void emptyNamedParametersTakePriorityOverTheirArrayAliases() throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param("q", "")
                .param("q[]", "ignored search")
                .param("after", "")
                .param("after[]", "not-base64-!!!"))
        .andExpect(status().isOk());

    assertThat(submittedQuery()).isEqualTo(MyLinksQuery.builder().build());
  }

  @ParameterizedTest
  @CsvSource({"-1, 20", "0, 20", "5000, 100"})
  void normalizesPageSizeAfterHttpBinding(String size, int expected) throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param("size", size))
        .andExpect(status().isOk());

    assertThat(submittedQuery().size()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "after, not-base64-!!!",
    "expiry, tomorrow",
    "createdAfter, not-a-date",
    "createdBefore, not-a-date",
    "sort, url",
    "dir, sideways"
  })
  void invalidStringConditionsKeepInvalidArgumentResponse(String name, String value)
      throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param(name, value))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

    verifyNoInteractions(service);
  }

  @Test
  void invalidNumericSizeIdentifiesTheInvalidClientParameter() throws Exception {
    mvc.perform(
            get("/api/v1/links/me")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .param("size", "not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
        .andExpect(jsonPath("$.parameter").value("size"));

    verifyNoInteractions(service);
  }

  private MyLinksQuery submittedQuery() {
    ArgumentCaptor<MyLinksQuery> query = ArgumentCaptor.forClass(MyLinksQuery.class);
    verify(service).myLinks(eq(USER_ID), query.capture());
    return query.getValue();
  }
}
