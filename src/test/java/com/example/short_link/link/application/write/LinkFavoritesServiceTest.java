package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.UserMutationLock;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkFavoritesServiceTest {

  private static final Long OWNER = 3L;

  @Mock private LinkRepository links;
  @Mock private UserMutationLock users;
  @Mock private LinkOwnership ownership;

  private LinkFavoritesService service;

  @BeforeEach
  void aService() {
    service = new LinkFavoritesService(links, users, ownership);
  }

  @Test
  void theFirstFavoriteTakesPositionZeroAfterTheOwnerIsLocked() {
    LinkEntity link = owned("fav0001");
    favorites();

    service.add(OWNER, link.getShortCode());

    assertThat(link.getFavoriteOrder()).isZero();
    InOrder serialized = inOrder(users, ownership, links);
    serialized.verify(users).lock(OWNER);
    serialized.verify(ownership).requireOwned(OWNER, link.getShortCode());
    serialized
        .verify(links)
        .findAllByUserIdAndFavoriteOrderIsNotNullOrderByFavoriteOrderAscIdAsc(OWNER);
  }

  @Test
  void aNewFavoriteGoesAfterTheLastOneEvenWhenPositionsHaveGaps() {
    LinkEntity link = owned("fav0002");
    favorites(favorite("fav000a", 0), favorite("fav000b", 4));

    service.add(OWNER, link.getShortCode());

    assertThat(link.getFavoriteOrder()).isEqualTo(5);
  }

  @Test
  void addingALinkThatIsAlreadyAFavoriteKeepsItsPosition() {
    LinkEntity link = owned("fav0003");
    link.changeFavoriteOrder(2);

    service.add(OWNER, link.getShortCode());

    assertThat(link.getFavoriteOrder()).isEqualTo(2);
  }

  @Test
  void removingAFavoriteClearsItsPositionAfterTheOwnerIsLocked() {
    LinkEntity link = owned("fav0004");
    link.changeFavoriteOrder(1);

    service.remove(OWNER, link.getShortCode());

    assertThat(link.getFavoriteOrder()).isNull();
    InOrder serialized = inOrder(users, ownership);
    serialized.verify(users).lock(OWNER);
    serialized.verify(ownership).requireOwned(OWNER, link.getShortCode());
  }

  @Test
  void reorderingNumbersTheFavoritesInTheGivenOrderAfterTheOwnerIsLocked() {
    LinkEntity a = favorite("fav000a", 0);
    LinkEntity b = favorite("fav000b", 1);
    LinkEntity c = favorite("fav000c", 2);
    favorites(a, b, c);

    service.reorder(OWNER, codes("fav000c", "fav000a", "fav000b"));

    assertThat(List.of(c.getFavoriteOrder(), a.getFavoriteOrder(), b.getFavoriteOrder()))
        .containsExactly(0, 1, 2);
    InOrder serialized = inOrder(users, links);
    serialized.verify(users).lock(OWNER);
    serialized
        .verify(links)
        .findAllByUserIdAndFavoriteOrderIsNotNullOrderByFavoriteOrderAscIdAsc(OWNER);
  }

  @ParameterizedTest
  @MethodSource("ordersThatAreNotTheSameFavorites")
  void reorderingRejectsAListThatIsNotExactlyTheCurrentFavorites(List<ShortCode> requested) {
    LinkEntity a = favorite("fav000a", 0);
    LinkEntity b = favorite("fav000b", 1);
    favorites(a, b);

    assertThatThrownBy(() -> service.reorder(OWNER, requested))
        .isInstanceOfSatisfying(
            LinkException.class,
            failure ->
                assertThat(failure.errorCode()).isEqualTo(LinkErrorCode.INVALID_FAVORITE_ORDER));
    assertThat(List.of(a.getFavoriteOrder(), b.getFavoriteOrder())).containsExactly(0, 1);
  }

  static List<List<ShortCode>> ordersThatAreNotTheSameFavorites() {
    return Arrays.asList(
        null,
        codes("fav000a"),
        codes("fav000a", "fav000a"),
        codes("fav000a", "fav000z"),
        codes("fav000b", "fav000a", "fav000z"));
  }

  private LinkEntity owned(String code) {
    LinkEntity link = new LinkEntity("https://example.com/" + code, code, OWNER, null);
    when(ownership.requireOwned(OWNER, link.getShortCode())).thenReturn(link);
    return link;
  }

  private void favorites(LinkEntity... current) {
    when(links.findAllByUserIdAndFavoriteOrderIsNotNullOrderByFavoriteOrderAscIdAsc(OWNER))
        .thenReturn(List.of(current));
  }

  private static LinkEntity favorite(String code, int order) {
    LinkEntity link = new LinkEntity("https://example.com/" + code, code, OWNER, null);
    link.changeFavoriteOrder(order);
    return link;
  }

  private static List<ShortCode> codes(String... values) {
    return Arrays.stream(values).map(ShortCode::new).toList();
  }
}
