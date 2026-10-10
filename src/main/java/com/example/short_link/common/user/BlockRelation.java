package com.example.short_link.common.user;

public record BlockRelation(boolean blockedByViewer, boolean blocksViewer) {

  public static final BlockRelation NONE = new BlockRelation(false, false);

  public boolean any() {
    return blockedByViewer || blocksViewer;
  }
}
