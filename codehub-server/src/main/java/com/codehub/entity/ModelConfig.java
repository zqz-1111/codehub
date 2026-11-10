package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_configs")
public class ModelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;    // 服务商：ALIYUN（阿里云）、DEEPSEEK、XIAOMI（小米）
    private String modelName;
    private String baseUrl;
    private String apiKey;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
