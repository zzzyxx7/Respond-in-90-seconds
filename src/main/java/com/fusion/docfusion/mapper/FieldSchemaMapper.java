package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.FieldSchema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FieldSchemaMapper {

    int insert(FieldSchema entity);

    FieldSchema selectById(@Param("id") Long id);

    FieldSchema selectByCode(@Param("code") String code);

    List<FieldSchema> selectAll();
}

