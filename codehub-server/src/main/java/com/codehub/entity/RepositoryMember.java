package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repository_members")
public class RepositoryMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repoId;
    private Long userId;
    private String role;  // OWNER/READ/WRITE
    private LocalDateTime createdAt;
}
