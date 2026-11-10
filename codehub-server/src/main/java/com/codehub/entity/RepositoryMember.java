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
    private String role;  // 角色：OWNER（所有者）、READ（只读）、WRITE（可写）
    private LocalDateTime createdAt;
}
