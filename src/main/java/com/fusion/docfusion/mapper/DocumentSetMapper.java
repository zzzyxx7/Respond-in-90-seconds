package com.fusion.docfusion.mapper;

import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.entity.DocumentSet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentSetMapper {
    int insert(DocumentSet entity);
    DocumentSet selectById(@Param("id") Long id);
    List<DocumentSetListItemVO> selectAllForList(@Param("ownerId") Long ownerId);
    int deleteById(@Param("id") Long id);
}
