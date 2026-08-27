package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("commits")
public class Commit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repoId;
    private String message;
    private Long authorId;
    private Long parentCommitId;
    private LocalDateTime createdAt;
}
