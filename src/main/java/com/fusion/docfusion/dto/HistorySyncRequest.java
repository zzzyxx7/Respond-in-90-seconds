package com.fusion.docfusion.dto;

import lombok.Data;

import java.util.List;

@Data
public class HistorySyncRequest {
    /**
     * 前端本地历史中的 publicId 列表（任务/模板）。
     */
    private List<String> publicIds;
}
