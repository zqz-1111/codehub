package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("files")
public class FileEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repoId;
    private String path;
    private String objectKey;
    private String mimeType;
    private Long sizeBytes;
    private Long commitId;
    private LocalDateTime createdAt;
}
