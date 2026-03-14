package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.ExtractedValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExtractedValueMapper {

    int insert(ExtractedValue entity);

    /**
     * 批量插入抽取结果
     */
    int insertBatch(@Param("list") List<ExtractedValue> list);

    List<ExtractedValue> selectByDocumentId(@Param("documentId") Long documentId);

    /**
     * 按文档删除已有抽取结果（同一文档重复抽取时先清再插，避免重复）
     */
    int deleteByDocumentId(@Param("documentId") Long documentId);
}

