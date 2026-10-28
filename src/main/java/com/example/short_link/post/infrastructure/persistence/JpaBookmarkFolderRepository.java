package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.BookmarkFolderEntity;
import com.example.short_link.post.domain.repository.BookmarkFolderRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBookmarkFolderRepository
    extends JpaRepository<BookmarkFolderEntity, Long>, BookmarkFolderRepository {}
