package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Persistent ownership, manifest and merge receipt for one upload. */
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
