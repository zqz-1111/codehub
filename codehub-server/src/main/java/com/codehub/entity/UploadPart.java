package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upload_parts")
public class UploadPart {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private Long repoId;
    private Integer chunkIndex;
    private String objectKey;
    private Long sizeBytes;
    private String status;  // 状态：UPLOADING（上传中）、MERGED（已合并）
    private LocalDateTime createdAt;
}
