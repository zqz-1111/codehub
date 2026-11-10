package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String email;
    private String role;      // 角色：USER（用户）、ADMIN（管理员）
    private String status;    // 状态：ACTIVE（正常）、BANNED（禁用）、PENDING（待处理）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
