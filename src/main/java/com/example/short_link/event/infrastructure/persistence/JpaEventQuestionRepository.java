package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaEventQuestionRepository
    extends JpaRepository<EventQuestionEntity, Long>, EventQuestionRepository {}
