package com.fusion.docfusion.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class FillTaskTokenStatsVO {
    private Long taskCount;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalTokens;
    private BigDecimal totalCost;
    private String totalCostCurrency;
    private Long missingUsageTaskCount;
    private List<BreakdownItem> providerBreakdowns = new ArrayList<>();
    private List<BreakdownItem> modeBreakdowns = new ArrayList<>();
    private List<BreakdownItem> statusBreakdowns = new ArrayList<>();
    private List<CurrencyBreakdownItem> currencyBreakdowns = new ArrayList<>();

    @Data
    public static class BreakdownItem {
        private String key;
        private Long taskCount;
        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private BigDecimal totalCost;
        private String totalCostCurrency;
    }

    @Data
    public static class CurrencyBreakdownItem {
        private String currency;
        private Long taskCount;
        private BigDecimal totalCost;
    }
}
