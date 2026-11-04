package com.example.short_link.live.application;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;

public interface LiveBus {

  void publish(LiveChange change);

  void publish(LiveRevoke revoke);
}
