package com.codehub.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepoVO {
    private Long id;
    private String name;
    private Long ownerId;
    private String ownerName;
    private String description;
    private String visibility;
    private String defaultBranch;
    private Integer starCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
