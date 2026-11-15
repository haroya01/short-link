package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.MyLinksCursor;
import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksQuery.SortDir;
import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import com.example.short_link.link.application.dto.MyLinksResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.MyLinksSearchCriteria;
import com.example.short_link.support.TestEntities;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyLinksClickSortPagingTest {

  private static final Long OWNER = 3L;
  private static final Instant EPOCH = Instant.parse("2026-11-01T00:00:00Z");
  private static final Map<Long, Long> CLICKS = Map.of(1L, 5L, 2L, 9L, 3L, 5L, 5L, 9L);

  @Mock private LinkRepository linkRepository;
  @Mock private MyLinkReader linkReader;
  @Mock private LinkTagLookup linkTags;
  @Captor private ArgumentCaptor<List<LinkEntity>> page;
  @Captor private ArgumentCaptor<Map<Long, Long>> shownCounts;

  private MyLinksQueryService service;

  @BeforeEach
  void fiveLinksTwoPairsTiedOnClicks() {
    service = new MyLinksQueryService(linkRepository, linkReader, linkTags);
    lenient().when(linkRepository.findMyLinksCandidates(any())).thenReturn(links(1, 2, 3, 4, 5));
    lenient().when(linkReader.clickCountsByLinkIds(anyList())).thenReturn(CLICKS);
  }

  @Test
  void mostClickedFirstBreaksTiesByNewestThenWalksEveryLinkOnce() {
    MyLinksResult first = service.myLinks(OWNER, byClicks(SortDir.DESC, 2, null));
    MyLinksResult second = service.myLinks(OWNER, byClicks(SortDir.DESC, 2, cursorOf(first)));
    MyLinksResult third = service.myLinks(OWNER, byClicks(SortDir.DESC, 2, cursorOf(second)));

    assertThat(pages()).containsExactly(List.of(5L, 2L), List.of(3L, 1L), List.of(4L));
    assertThat(cursorOf(first)).isEqualTo(new MyLinksCursor(createdAt(2), 2L, 9L));
    assertThat(cursorOf(second)).isEqualTo(new MyLinksCursor(createdAt(1), 1L, 5L));
    assertThat(first.hasMore()).isTrue();
    assertThat(second.hasMore()).isTrue();
    assertThat(third.hasMore()).isFalse();
    assertThat(third.nextCursor()).isNull();
  }

  @Test
  void leastClickedFirstStillPutsTheNewerOfATieFirst() {
    service.myLinks(OWNER, byClicks(SortDir.ASC, 5, null));

    assertThat(pages()).containsExactly(List.of(4L, 3L, 1L, 5L, 2L));
  }

  @Test
  void aPageThatEndsOnTheLastLinkOffersNoCursor() {
    MyLinksResult all = service.myLinks(OWNER, byClicks(SortDir.DESC, 5, null));

    assertThat(all.hasMore()).isFalse();
    assertThat(all.nextCursor()).isNull();
  }

  @Test
  void aCursorWhoseLinkWasDeletedResumesAtTheNextPositionInTheOrder() {
    when(linkRepository.findMyLinksCandidates(any())).thenReturn(links(1, 3, 4, 5));
    MyLinksCursor deleted = new MyLinksCursor(createdAt(2), 2L, 9L);

    service.myLinks(OWNER, byClicks(SortDir.DESC, 2, deleted));

    assertThat(pages()).containsExactly(List.of(3L, 1L));
  }

  @Test
  void aCursorPastEveryRemainingLinkGivesAnEmptyLastPage() {
    when(linkRepository.findMyLinksCandidates(any())).thenReturn(links(1, 2, 3, 5));
    MyLinksCursor deletedLast = new MyLinksCursor(createdAt(4), 4L, 0L);

    MyLinksResult result = service.myLinks(OWNER, byClicks(SortDir.DESC, 2, deletedLast));

    assertThat(result).isEqualTo(new MyLinksResult(List.of(), null, false));
    verify(linkReader, never()).assemble(anyList(), anyMap());
  }

  @Test
  void aCreatedAtCursorForADeletedLinkCannotBePlacedSoThePageIsEmpty() {
    when(linkRepository.findMyLinksCandidates(any())).thenReturn(links(1, 3, 4, 5));
    MyLinksCursor withoutClicks = new MyLinksCursor(createdAt(2), 2L);

    MyLinksResult result = service.myLinks(OWNER, byClicks(SortDir.DESC, 2, withoutClicks));

    assertThat(result).isEqualTo(new MyLinksResult(List.of(), null, false));
  }

  @Test
  void aCursorWhoseLinkGainedClicksSinceStillResumesRightAfterThatLink() {
    MyLinksCursor stale = new MyLinksCursor(createdAt(2), 2L, 3L);

    service.myLinks(OWNER, byClicks(SortDir.DESC, 2, stale));

    assertThat(pages()).containsExactly(List.of(3L, 1L));
  }

  @Test
  void sortingByPeopleOrdersByHumanClicksButShowsEveryClick() {
    when(linkRepository.findMyLinksCandidates(any())).thenReturn(links(1, 2, 3));
    when(linkReader.humanClickCountsByLinkIds(List.of(1L, 2L, 3L)))
        .thenReturn(Map.of(1L, 1L, 3L, 2L));
    Map<Long, Long> everyClick = Map.of(3L, 30L, 1L, 10L);
    when(linkReader.clickCountsByLinkIds(List.of(3L, 1L))).thenReturn(everyClick);

    MyLinksResult result =
        service.myLinks(
            OWNER,
            MyLinksQuery.builder()
                .size(2)
                .sort(SortKey.HUMAN_CLICK_COUNT)
                .dir(SortDir.DESC)
                .build());

    verify(linkReader).assemble(page.capture(), shownCounts.capture());
    assertThat(ids(page.getValue())).containsExactly(3L, 1L);
    assertThat(shownCounts.getValue()).isSameAs(everyClick);
    assertThat(cursorOf(result)).isEqualTo(new MyLinksCursor(createdAt(1), 1L, 1L));
  }

  @Test
  void anOwnerWithoutLinksGetsAnEmptyPageWithoutCountingClicks() {
    when(linkRepository.findMyLinksCandidates(any())).thenReturn(List.of());

    MyLinksResult result = service.myLinks(OWNER, byClicks(SortDir.DESC, 2, null));

    assertThat(result).isEqualTo(new MyLinksResult(List.of(), null, false));
    verify(linkReader, never()).clickCountsByLinkIds(anyList());
  }

  @Test
  void newestFirstAsksForOneExtraRowAndOffersACursorOnlyWhenItCame() {
    when(linkRepository.findMyLinksCreatedAtPage(any(), isNull(), isNull(), eq(false), eq(3)))
        .thenReturn(links(5, 4, 3));
    MyLinksResult more = service.myLinks(OWNER, MyLinksQuery.builder().size(2).build());

    MyLinksCursor after = cursorOf(more);
    when(linkRepository.findMyLinksCreatedAtPage(
            any(), eq(after.createdAt()), eq(after.id()), eq(false), eq(3)))
        .thenReturn(links(3, 2));
    MyLinksResult last =
        service.myLinks(OWNER, MyLinksQuery.builder().size(2).after(after).build());

    assertThat(pages()).containsExactly(List.of(5L, 4L), List.of(3L, 2L));
    assertThat(more.hasMore()).isTrue();
    assertThat(after).isEqualTo(new MyLinksCursor(createdAt(4), 4L));
    assertThat(last.hasMore()).isFalse();
    assertThat(last.nextCursor()).isNull();
  }

  @Test
  void theSearchTagDomainAndDatesReachTheRepositoryAsOneCriteria() {
    Instant from = EPOCH;
    Instant to = EPOCH.plus(Duration.ofDays(7));
    when(linkTags.linkIdsForTag(OWNER, "work")).thenReturn(Optional.of(List.of(7L)));
    MyLinksSearchCriteria criteria =
        new MyLinksSearchCriteria(OWNER, "spring", List.of(7L), "example.com", null, from, to);

    MyLinksResult result =
        service.myLinks(
            OWNER,
            MyLinksQuery.builder()
                .size(2)
                .q(" spring ")
                .tag("work")
                .domain("example.com")
                .createdAfter(from)
                .createdBefore(to)
                .build());

    verify(linkRepository).findMyLinksCreatedAtPage(criteria, null, null, false, 3);
    assertThat(result).isEqualTo(new MyLinksResult(List.of(), null, false));
    verify(linkReader, never()).assemble(anyList(), anyMap());
  }

  @Test
  void oldestFirstReadsThePageInAscendingOrder() {
    when(linkRepository.findMyLinksCreatedAtPage(any(), isNull(), isNull(), eq(true), eq(3)))
        .thenReturn(links(1, 2));

    service.myLinks(OWNER, MyLinksQuery.builder().size(2).dir(SortDir.ASC).build());

    assertThat(pages()).containsExactly(List.of(1L, 2L));
  }

  private List<List<Long>> pages() {
    verify(linkReader, atLeastOnce()).assemble(page.capture(), anyMap());
    return page.getAllValues().stream().map(MyLinksClickSortPagingTest::ids).toList();
  }

  private static List<Long> ids(List<LinkEntity> links) {
    return links.stream().map(LinkEntity::getId).toList();
  }

  private static MyLinksCursor cursorOf(MyLinksResult result) {
    return MyLinksCursor.decode(result.nextCursor());
  }

  private static MyLinksQuery byClicks(SortDir dir, int size, MyLinksCursor after) {
    return MyLinksQuery.builder()
        .size(size)
        .sort(SortKey.CLICK_COUNT)
        .dir(dir)
        .after(after)
        .build();
  }

  private static List<LinkEntity> links(long... ids) {
    return Arrays.stream(ids).mapToObj(MyLinksClickSortPagingTest::link).toList();
  }

  private static LinkEntity link(long id) {
    LinkEntity link =
        TestEntities.withId(
            new LinkEntity("https://example.com/" + id, "paging" + id, OWNER, null), id);
    return TestEntities.setField(link, "createdAt", createdAt(id));
  }

  private static Instant createdAt(long id) {
    return EPOCH.plus(Duration.ofMinutes(id));
  }
}
