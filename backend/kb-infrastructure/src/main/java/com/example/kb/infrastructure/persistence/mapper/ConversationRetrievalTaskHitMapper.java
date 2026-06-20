package com.example.kb.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.kb.infrastructure.persistence.entity.ConversationRetrievalTaskHitEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversationRetrievalTaskHitMapper extends BaseMapper<ConversationRetrievalTaskHitEntity> {

    int insertBatch(@Param("entities") List<ConversationRetrievalTaskHitEntity> entities);
}
