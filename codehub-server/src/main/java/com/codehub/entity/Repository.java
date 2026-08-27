package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repositories")
public class Repository {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long ownerId;
    private String description;
    private String visibility;  // PUBLIC/PRIVATE
    private String defaultBranch;
    private Integer starCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
