package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;

public final class ProfileLinkSlot {

  private final LinkEntity link;

  ProfileLinkSlot(LinkEntity link) {
    this.link = link;
  }

  public LinkId id() {
    return link.linkId();
  }

  public void placeAt(Integer order) {
    link.setProfileOrder(order);
  }

  public void feature() {
    link.featureOnProfile();
  }

  public void unfeature() {
    link.unfeatureOnProfile();
  }
}
