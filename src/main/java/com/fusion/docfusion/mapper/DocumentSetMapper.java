package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.DocumentSet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentSetMapper {
    int insert(DocumentSet entity);
    DocumentSet selectById(@Param("id") Long id);
}
