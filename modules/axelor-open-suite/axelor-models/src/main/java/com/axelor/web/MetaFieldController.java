package com.axelor.web;

import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.service.MetaJsonFieldService;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

public class MetaFieldController {

    @Inject
    private MetaJsonFieldService metaJsonFieldService;

    public void getFieldsForSearch(ActionRequest request, ActionResponse response) {
        Map<String, Object> map = (Map<String, Object>) request.getContext().get("_parent");
        Integer id = (Integer) map.getOrDefault("id", 0);
        List<String> searchFields = metaJsonFieldService.findByModelId(id.longValue());

        if (searchFields.size() == 0) {
            response.setError(
                    "Модуль еще не создан или у модуля нет полей, которые могли бы отображаться при поиске!");
            response.setValue("isSearchField", false);
            return;
        }

        response.setValue("templateField", searchFields);
    }

}
