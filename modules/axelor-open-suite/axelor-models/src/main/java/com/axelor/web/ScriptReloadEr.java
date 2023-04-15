package com.axelor.web;

import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.repo.MetaJsonFieldRepository;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import java.util.List;
import javax.annotation.security.PermitAll;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/meta")
@Singleton
public class ScriptReloadEr {

  @GET
  @Path("/check/models")
  public Response checkNewModels() {
    MetaJsonModelRepository metaJsonModelRepository = Beans.get(MetaJsonModelRepository.class);
    List<MetaJsonModel> models =
        metaJsonModelRepository.all().filter("isCompleted is ?", true).fetch();
    return Response.ok(models.size()).build();
  }

  @GET
  @Path("/check/fields")
  public Response checkNewFields() {
    MetaJsonFieldRepository metaJsonFieldRepository = Beans.get(MetaJsonFieldRepository.class);
    List<MetaJsonField> models =
        metaJsonFieldRepository.all().filter("model not like '%MetaJsonRecord%'").fetch();
    return Response.ok(models.size()).build();
  }
}
