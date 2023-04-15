package com.axelor.module;

import com.axelor.app.AxelorModule;
import com.axelor.service.MappingService;

public class CustomModelModule extends AxelorModule {
  @Override
  protected void configure() {
    bind(MappingService.class);
  }
}
