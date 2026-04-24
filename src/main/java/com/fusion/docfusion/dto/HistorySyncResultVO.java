package com.fusion.docfusion.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HistorySyncResultVO {
    private Integer total;
    private Integer claimed;
    private Integer alreadyOwned;
    private Integer notFound;
    private Integer forbidden;
    private List<String> claimedIds = new ArrayList<>();
    private List<String> alreadyOwnedIds = new ArrayList<>();
    private List<String> notFoundIds = new ArrayList<>();
    private List<String> forbiddenIds = new ArrayList<>();
}
