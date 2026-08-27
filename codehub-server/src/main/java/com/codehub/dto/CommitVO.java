package com.codehub.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommitVO {
    private Long id;
    private String message;
    private Long authorId;
    private String authorName;
    private LocalDateTime createdAt;
}
