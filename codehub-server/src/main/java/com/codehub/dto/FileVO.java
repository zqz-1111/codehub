package com.codehub.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileVO {
    private Long id;
    private String path;
    private String mimeType;
    private Long sizeBytes;
    private Long commitId;
    private LocalDateTime createdAt;
}
