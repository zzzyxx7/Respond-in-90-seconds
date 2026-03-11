package com.fusion.docfusion.service;

import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.FillTask;

import java.util.List;

public interface AiFillService {
    void fillTemplateForTask(FillTask task, List<Document> docs);
}