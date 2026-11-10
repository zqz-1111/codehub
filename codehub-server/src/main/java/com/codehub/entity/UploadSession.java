package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 持久化保存一次上传的归属、分片清单和合并回执。 */
@Data
@TableName("upload_sessions")
public class UploadSession {
    @TableId(type = IdType.INPUT)
    private String uploadId;
    private Long repoId;
    private Long userId;
    private Integer totalChunks;
    private String status;
    private Long mergedFileId;
    private String mergedPath;
    private String mergedFileName;
}
