package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.FillTaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FillTaskStepMapper {

    int upsert(FillTaskStep step);

    List<FillTaskStep> selectByTaskId(@Param("taskId") Long taskId);

    int deleteByTaskId(@Param("taskId") Long taskId);
}

