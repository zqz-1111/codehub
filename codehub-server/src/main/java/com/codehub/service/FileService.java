package com.codehub.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.codehub.dto.CommitVO;
import com.codehub.dto.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {

    /**
     * 上传文件
     */
    FileVO uploadFile(Long repoId, Long userId, String path, MultipartFile file);

    /**
     * 获取文件预签名下载URL
     */
    String getFileDownloadUrl(Long repoId, Long userId, Long fileId);

    /**
     * 获取文件内容（用于代码展示）
     */
    byte[] getFileContent(Long repoId, Long userId, Long fileId);

    /**
     * 删除文件
     */
    void deleteFile(Long repoId, Long userId, Long fileId);

    /**
     * 列出仓库下的文件
     */
    List<FileVO> listFiles(Long repoId, Long userId);

    /**
     * 列出仓库的提交历史
     */
    IPage<CommitVO> listCommits(Long repoId, Long userId, int page, int size);

    /**
     * 上传分片
     */
    void uploadChunk(Long repoId, Long userId, String uploadId, int chunkIndex, int totalChunks, MultipartFile file);

    /**
     * 合并分片
     */
    FileVO mergeChunks(Long repoId, Long userId, String uploadId, String fileName, String path);
}
