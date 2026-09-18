package com.example.short_link.link.application.write;

import com.example.short_link.common.lock.UserMutationLock;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkFavoritesService {
  private final LinkRepository links;
  private final UserMutationLock users;
  private final LinkOwnership ownership;

  @Transactional
  public void add(Long userId, ShortCode code) {
    lockOwner(userId);
    LinkEntity link = ownership.requireOwned(userId, code);
    if (link.getFavoriteOrder() != null) return;
    int next =
        favorites(userId).stream().mapToInt(LinkEntity::getFavoriteOrder).max().orElse(-1) + 1;
    link.changeFavoriteOrder(next);
  }

  @Transactional
  public void remove(Long userId, ShortCode code) {
    lockOwner(userId);
    ownership.requireOwned(userId, code).changeFavoriteOrder(null);
  }

  @Transactional
  public void reorder(Long userId, List<ShortCode> codes) {
    lockOwner(userId);
    List<LinkEntity> favorites = favorites(userId);
    Map<ShortCode, LinkEntity> byCode = new HashMap<>();
    favorites.forEach(link -> byCode.put(link.getShortCode(), link));
    if (codes == null
        || codes.size() != favorites.size()
        || new HashSet<>(codes).size() != codes.size()
        || !byCode.keySet().containsAll(codes)) {
      throw new LinkException(LinkErrorCode.INVALID_FAVORITE_ORDER);
    }
    for (int i = 0; i < codes.size(); i++) byCode.get(codes.get(i)).changeFavoriteOrder(i);
  }

  private List<LinkEntity> favorites(Long userId) {
    return links.findAllByUserIdAndFavoriteOrderIsNotNullOrderByFavoriteOrderAscIdAsc(userId);
  }

  // Serialize app/web mutations even when the first favorite has not been created yet.
  private void lockOwner(Long userId) {
    users.lock(userId);
  }
}
