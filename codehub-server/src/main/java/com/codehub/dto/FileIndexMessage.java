package com.codehub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件索引构建消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileIndexMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long repoId;
    private Long fileId;
    private String filePath;
    private String content;

    /** 幂等消息ID（防重复消费） */
    private String messageId;
}
