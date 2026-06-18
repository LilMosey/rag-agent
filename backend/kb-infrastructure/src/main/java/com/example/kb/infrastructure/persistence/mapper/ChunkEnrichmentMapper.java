package com.example.kb.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.kb.infrastructure.persistence.entity.ChunkEnrichmentEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChunkEnrichmentMapper extends BaseMapper<ChunkEnrichmentEntity> {

    int insertBatch(@Param("entities") List<ChunkEnrichmentEntity> entities);
}
