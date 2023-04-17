package com.axelor.service;

import com.axelor.app.AppSettings;
import com.axelor.db.JPA;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.event.Observes;
import com.axelor.events.StartupEvent;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.db.repo.MetaJsonFieldRepository;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import com.axelor.meta.db.repo.MetaJsonRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MappingService {

  @Inject private ObjectMapper objectMapper;
  private String path;
  private Set<String> paths;

  private final Logger logger = LoggerFactory.getLogger(MappingService.class);

  public void migrateDataFromMetaJsonRecord(@Observes StartupEvent event) {
    logger.info("starting migrate".toUpperCase());
    path = AppSettings.get().get("xgtool.path");
    if (path == null) {
      logger.info("xgtool not configured".toUpperCase());
      return;
    }
    logger.info("path {}", path);
    paths = getPaths();

    MetaJsonModelRepository metaJsonModelRepository = Beans.get(MetaJsonModelRepository.class);
    MetaJsonRecordRepository metaJsonRecordRepository = Beans.get(MetaJsonRecordRepository.class);
    MetaJsonFieldRepository metaJsonFieldRepository = Beans.get(MetaJsonFieldRepository.class);

    List<MetaJsonField> archive =
        metaJsonFieldRepository.all().filter("model like '%MetaJsonRecord%'").fetch();

    List<MetaJsonRecord> records = metaJsonRecordRepository.all().fetch();
    Map<String, List<MetaJsonRecord>> models = new HashMap<>();

    List<MetaJsonModel> fetch =
        metaJsonModelRepository.all().filter("isCompleted is ?", true).fetch();
    List<String> names = fetch.stream().map(MetaJsonModel::getName).collect(Collectors.toList());
    for (MetaJsonRecord record : records) {
      String key = record.getJsonModel();
      if (record.getJsonModel() == null || record.getJsonModel().trim().isEmpty()) {
        continue;
      }
      if (!names.contains(record.getJsonModel())) {
        continue;
      }
      if (models.containsKey(key)) {
        models.get(key).add(record);
      } else {
        ArrayList<MetaJsonRecord> strings = new ArrayList<>();
        strings.add(record);
        models.put(key, strings);
      }
    }

    for (String modelName : models.keySet()) {
      logger.info("try migrate for {}".toUpperCase(), modelName);

      try {
        Class<? extends Model> klass = null;
        for (String path : paths) {
          try {
            klass = (Class<? extends Model>) Class.forName(String.format("%s.%s", path, modelName));
          } catch (Exception ignored) {
          }
          if (klass != null) {
            break;
          }
        }

        if (klass == null) {
          throw new RuntimeException("NOT FOUND");
        }
        for (MetaJsonRecord valueJson : models.get(modelName)) {
          Map<String, Object> value = objectMapper.readValue(valueJson.getAttrs(), HashMap.class);
          for (Map.Entry<String, Object> val : value.entrySet()) {
            if (val.getValue() instanceof Map) {
              Map<String, Object> child = (HashMap<String, Object>) val.getValue();
              createFields(child, val.getKey(), records, archive);
            }
            if (val.getValue() instanceof List) {
              List<Object> childVals = (ArrayList<Object>) val.getValue();
              for (Object child : childVals) {
                Map<String, Object> childProp = (HashMap<String, Object>) child;
                createFields(childProp, val.getKey(), records, archive);
              }
            }
          }
          if (!JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().begin();
          }
          JPA.verify(klass, value);
          Model model = JPA.edit(klass, value);
          model = JPA.save(model);
          // if success
          metaJsonRecordRepository.remove(valueJson);
          JPA.em().getTransaction().commit();
          logger.info("saved id: {} {}".toUpperCase(), model.getId(), modelName);
          setNewValueForOtherParents(records, archive, model, valueJson.getId(), model.getId());
        }

        Optional<MetaJsonModel> jsonModel =
            Optional.ofNullable(metaJsonModelRepository.findByName(modelName));

        jsonModel.ifPresent(this::deleteCustomFields);
        Thread.sleep(3000);
        if (!JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().begin();
        }
        jsonModel.ifPresent(metaJsonModelRepository::remove);
        JPA.em().getTransaction().commit();
      } catch (Exception ex) {
        //        try {
        if (JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().rollback();
        }
        //        } catch (Exception exception) {
        //          logger.error("ERROR: {}", exception.getMessage());
        //        }
        logger.error("ERROR: {}", ex.getMessage());
      }
    }
    try {
      List<MetaJsonModel> jsonModels =
          metaJsonModelRepository.all().filter("isCompleted is ?", true).fetch();
      for (MetaJsonModel jsonModel : jsonModels) {
        long count =
            metaJsonRecordRepository.all().filter("jsonModel like ?", jsonModel.getName()).count();
        if (count < 1) {
          deleteCustomFields(jsonModel);
          Thread.sleep(3000);
          if (!JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().begin();
          }
          metaJsonModelRepository.remove(jsonModel);
          logger.info("DELETED {}", jsonModel.getName());
          JPA.em().getTransaction().commit();
        }
      }
    } catch (Exception exception) {
      if (JPA.em().getTransaction().isActive()) {
        JPA.em().getTransaction().rollback();
      }
      logger.warn(exception.getMessage());
    }
  }

  private Set<String> getPaths() {
    try {
      Query query =
          JPA.em()
              .createQuery(
                  "select packageName from com.axelor.meta.db.MetaModel group by packageName");
      List managers = query.getResultList();
      managers.add(path);
      return new HashSet<String>(managers);
    } catch (Exception exception) {
      return new HashSet<>();
    }
  }

  private void createFields(
      Map<String, Object> child,
      String fieldName,
      List<MetaJsonRecord> records,
      List<MetaJsonField> archive)
      throws Exception {
    for (Map.Entry<String, Object> childVal : child.entrySet()) {
      if (childVal.getValue() != null && childVal.getKey().equals("id")) {
        MetaJsonField fieldByName = getByNameInSelect(archive, fieldName);
        if (fieldByName == null) break;
        Class<? extends Model> childClass = null;
        if (fieldByName.getTargetJsonModel() != null) {
          for (String path : paths) {
            try {
              childClass =
                  (Class<? extends Model>)
                      Class.forName(
                          String.format("%s.%s", path, fieldByName.getTargetJsonModel().getName()));
            } catch (Exception ignore) {
            }
            if (childClass != null) {
              break;
            }
          }
        } else {
          childClass = (Class<? extends Model>) Class.forName(fieldByName.getTargetModel());
        }
        if (JPA.em().getTransaction().isActive()) JPA.em().getTransaction().rollback();
        JpaRepository<? extends Model> childRepo = JpaRepository.of(childClass);
        if (childRepo.find(Long.parseLong(childVal.getValue() + "")) != null) {
          break;
        }

        if (!JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().begin();
        }
        MetaJsonRecord byIdInRecords =
            getByIdInRecords(records, Long.parseLong(childVal.getValue() + ""));
        if (byIdInRecords != null) {
          String attrs = byIdInRecords.getAttrs();
          HashMap values = objectMapper.readValue(attrs, HashMap.class);
          HashMap<String, Object> objectHashMap = new HashMap<>(values);
          for (Object childValVal : values.entrySet()) {
            if (((Map.Entry<String, Object>) childValVal).getValue() instanceof Map) {
              objectHashMap.remove(((Map.Entry<String, Object>) childValVal).getKey());
            }
            if (((Map.Entry<String, Object>) childValVal).getValue() instanceof List) {
              objectHashMap.remove(((Map.Entry<String, Object>) childValVal).getKey());
            }
          }
          Model childObj = childRepo.create(objectHashMap);
          Model model = JPA.save(childObj);
          JPA.em().getTransaction().commit();
          values.put("id", model.getId());
          child.put("id", model.getId());
          byIdInRecords.setAttrs(objectMapper.writeValueAsString(values));
          logger.info(
              "saved child id: {} {}".toUpperCase(), model.getId(), childClass.getCanonicalName());
          setNewValueForOtherParents(records, archive, model, byIdInRecords.getId(), model.getId());
        } else {
          logger.info("child record not found {}".toUpperCase(), childClass.getCanonicalName());
        }
      }
    }
  }

  private void deleteCustomFields(MetaJsonModel model) {
    MetaJsonFieldRepository metaJsonFieldRepository = Beans.get(MetaJsonFieldRepository.class);
    List<MetaJsonField> fields =
        metaJsonFieldRepository.all().filter("jsonModel is ?", model.getId()).fetch();
    for (MetaJsonField field : fields) {
      if (!JPA.em().getTransaction().isActive()) {
        JPA.em().getTransaction().begin();
      }
      metaJsonFieldRepository.remove(field);
      JPA.em().getTransaction().commit();
    }
  }

  private MetaJsonField getByNameInSelect(List<MetaJsonField> fields, String name) {
    for (MetaJsonField field : fields) {
      if (field.getName().equals(name)) {
        return field;
      }
    }
    return null;
  }

  private MetaJsonRecord getByIdInRecords(List<MetaJsonRecord> records, Long id) {
    for (MetaJsonRecord record : records) {
      if (record.getId().equals(id)) {
        return record;
      }
    }
    return null;
  }

  private void setNewValueForOtherParents(
      List<MetaJsonRecord> records,
      List<MetaJsonField> fields,
      Model model,
      Object key,
      Object value)
      throws JsonProcessingException {
    for (MetaJsonRecord record : records) {
      HashMap<String, Object> recordTypeMap =
          objectMapper.readValue(record.getAttrs(), HashMap.class);
      for (Map.Entry<String, Object> field : recordTypeMap.entrySet()) {
        if (field.getValue() instanceof Map) {
          MetaJsonField metaJsonField = getByNameInSelect(fields, field.getKey());
          if (metaJsonField == null || metaJsonField.getTargetJsonModel() == null) continue;
          String format =
              String.format("%s.%s", path, metaJsonField.getTargetJsonModel().getName());
          if (model.getClass().getCanonicalName().equals(format)) {
            HashMap<String, Object> fieldTypeMapValue = (HashMap<String, Object>) field.getValue();
            Object id = Long.parseLong(fieldTypeMapValue.get("id") + "");
            if (id.equals(key)) {
              fieldTypeMapValue.put("id", value);
            }
          }
        }
        if (field.getValue() instanceof List) {
          MetaJsonField metaJsonField = getByNameInSelect(fields, field.getKey());
          if (metaJsonField == null || metaJsonField.getTargetJsonModel() == null) continue;
          String format =
              String.format("%s.%s", path, metaJsonField.getTargetJsonModel().getName());
          if (model.getClass().getCanonicalName().equals(format)) {
            List<Object> objects = (ArrayList<Object>) field.getValue();
            for (Object obj : objects) {
              HashMap<String, Object> fieldTypeMapValue = (HashMap<String, Object>) obj;
              Object id = Long.parseLong(fieldTypeMapValue.get("id") + "");
              if (id.equals(key)) {
                fieldTypeMapValue.put("id", value);
              }
            }
          }
        }
      }
      record.setAttrs(objectMapper.writeValueAsString(recordTypeMap));
    }
  }

  public void migrateDataFromAttrs(@Observes StartupEvent event) {
    MetaJsonFieldRepository metaJsonFieldRepository = Beans.get(MetaJsonFieldRepository.class);
    List<MetaJsonField> fields =
        metaJsonFieldRepository.all().filter("model not like '%MetaJsonRecord%'").fetch();

    logger.info("starting migrate attrs data to fields".toUpperCase());

    List<String> models =
        fields.stream().map(MetaJsonField::getModel).distinct().collect(Collectors.toList());

    for (String model : models) {
      logger.info("try migrate fields for {}".toUpperCase(), model);
      try {
        Class<? extends Model> klass = (Class<? extends Model>) Class.forName(model);
        JpaRepository repo = JpaRepository.of(klass);
        List items = repo.all().fetch();

        for (Object item : items) {
          Map map = objectMapper.convertValue(item, Map.class);
          if (map.get("attrs") == null) {
            continue;
          }
          String attrs = String.valueOf(map.get("attrs"));
          Map attrsMap = objectMapper.readValue(attrs, HashMap.class);

          map.putAll(attrsMap);
          map.put("attrs", null);
          if (!JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().begin();
          }
          JPA.verify(klass, map);
          Model modelObj = JPA.edit(klass, map);
          modelObj = JPA.merge(modelObj);
          JPA.em().getTransaction().commit();
          logger.info("updated id: {} {}".toUpperCase(), modelObj.getId(), model);
        }
        if (JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().rollback();
        }
        List<MetaJsonField> jsonFields =
            metaJsonFieldRepository.all().filter("model is ?", model).fetch();
        for (MetaJsonField field : jsonFields) {
          try {
            if (!JPA.em().getTransaction().isActive()) {
              JPA.em().getTransaction().begin();
            }
            metaJsonFieldRepository.remove(field);
            JPA.em().getTransaction().commit();
          } catch (RuntimeException exception) {
            if (JPA.em().getTransaction().isActive()) {
              JPA.em().getTransaction().rollback();
            }
            continue;
          }
          logger.info("field deleted id: {} {}".toUpperCase(), field.getId(), model);
        }
      } catch (Exception exception) {
        if (JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().rollback();
        }
        logger.warn("error migrate fields data: {}".toUpperCase(), model);
        logger.error(exception.getMessage());
      }
    }
  }
}
