package com.fusion.docfusion.service;

import com.fusion.docfusion.entity.FieldSchema;

import java.util.List;

public interface FieldSchemaService {

    FieldSchema create(FieldSchema schema);

    List<FieldSchema> listAll();
}
