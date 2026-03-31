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

    /**
     * 重跑前重置任务状态与结果字段。
     */
    int resetForRerun(@Param("id") Long id,
                      @Param("fromStatus1") String fromStatus1,
                      @Param("fromStatus2") String fromStatus2,
                      @Param("toStatus") String toStatus,
                      @Param("message") String message);

    /**
     * 按状态条件取消任务（用于并发下安全取消）。
     */
    int cancelIfStatusIn(@Param("id") Long id,
                         @Param("status1") String status1,
                         @Param("status2") String status2,
                         @Param("cancelledStatus") String cancelledStatus,
                         @Param("message") String message,
                         @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 扫描超时 RUNNING 任务（用于恢复补偿）。
     */
    List<FillTask> selectRunningTimeoutTasks(@Param("cutoff") LocalDateTime cutoff,
                                             @Param("limit") Integer limit);

    /**
     * 将 RUNNING 任务原子标记为 TIMEOUT（并发安全）。
     */
    int markRunningTimeout(@Param("id") Long id,
                           @Param("timeoutStatus") String timeoutStatus,
                           @Param("message") String message);
}
