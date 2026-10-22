package com.codehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codehub.entity.UploadSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UploadSessionMapper extends BaseMapper<UploadSession> {
    @Select("SELECT * FROM upload_sessions WHERE upload_id = #{uploadId} FOR UPDATE")
    UploadSession selectForUpdate(@Param("uploadId") String uploadId);
}
