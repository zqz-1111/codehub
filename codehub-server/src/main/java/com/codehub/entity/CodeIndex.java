package com.codehub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("code_indexes")
public class CodeIndex {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long repoId;
    private Long fileId;
    private String chunkType;     // CLASS / METHOD / FILE
    private String chunkName;     // 类名或方法名
    private String content;       // 代码内容
    private String imports;       // import列表
    private String pathFeatures;  // 文件路径特征
    private String embedding;     // 向量（JSON数组字符串）
    private LocalDateTime createdAt;
}
