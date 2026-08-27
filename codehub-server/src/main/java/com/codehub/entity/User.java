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
    private String role;      // USER/ADMIN
    private String status;    // ACTIVE/BANNED/PENDING
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
