package com.axelor.service.impl;

import com.axelor.meta.db.MetaJsonField;
import com.axelor.service.MetaJsonFieldService;
import com.axelor.studio.db.repo.MetaJsonFieldRepo;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class MetaJsonFieldServiceImpl implements MetaJsonFieldService {
  @Inject private MetaJsonFieldRepo metaJsonFieldRepo;

  @Override
  public List<String> findByModelId(Long id) {
    List<MetaJsonField> fields =
        metaJsonFieldRepo
            .all()
            .filter(
                "self.jsonModel.id = :id AND self.type IN ('string', 'integer', 'decimal', 'boolean', 'datetime', 'date', 'time')")
            .bind("id", id)
            .fetch();
    return fields.stream().map(MetaJsonField::getName).collect(Collectors.toList());
  }
}
