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
}

