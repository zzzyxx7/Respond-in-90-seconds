package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {
    int insert(Document entity);
    List<Document> selectByDocumentSetId(@Param("documentSetId") Long documentSetId);
    Document selectById(@Param("id") Long id);
}
