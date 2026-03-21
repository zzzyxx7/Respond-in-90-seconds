package com.fusion.docfusion.mapper;

import com.fusion.docfusion.entity.FillTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FillTaskMapper {
    int insert(FillTask entity);
    int updateById(FillTask entity);
    FillTask selectById(@Param("id") Long id);

    List<FillTask> selectByConditions(@Param("userId") Long userId,
                                      @Param("mode") String mode,
                                      @Param("status") String status,
                                      @Param("limit") Integer limit,
                                      @Param("offset") Integer offset);

    long countByConditions(@Param("userId") Long userId,
                           @Param("mode") String mode,
                           @Param("status") String status);

    /**
     * 查询需要清理结果文件的历史任务（已成功、已有结果文件、完成时间早于 cutoff）。
     */
    List<FillTask> selectExpiredResultTasks(@Param("cutoff") LocalDateTime cutoff,
                                            @Param("limit") Integer limit);

    /**
     * 标记结果文件已被生命周期策略清理。
     */
    int markResultExpired(@Param("id") Long id,
                          @Param("message") String message);
}
