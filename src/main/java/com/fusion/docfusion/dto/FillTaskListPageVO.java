package com.fusion.docfusion.dto;

import lombok.Data;

import java.util.List;

/**
 * 填表任务分页列表：含总数与是否还有下一页，便于前端翻页。
 */
@Data
public class FillTaskListPageVO {

    private List<FillTaskVO> list;

    /** 符合条件的总条数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 是否还有下一页 */
    private boolean hasMore;
}
