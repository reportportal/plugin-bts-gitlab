package com.epam.reportportal.extension.gitlab.command;

import java.util.List;

public abstract class PredefinedFieldTypes {

  public static final String AUTOCOMPLETE = "autocomplete";
  public static final String MULTI_AUTOCOMPLETE = "multiAutocomplete";
  public static final String CREATABLE_MULTI_AUTOCOMPLETE = "creatableMultiAutocomplete";

  public static final List<String> NAMED_VALUE_FIELDS = List.of(AUTOCOMPLETE, MULTI_AUTOCOMPLETE,
      CREATABLE_MULTI_AUTOCOMPLETE);


}
