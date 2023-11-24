/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.extension.gitlab.command;

import static com.epam.reportportal.extension.gitlab.command.GetIssueFieldsCommand.ISSUE_TYPE;
import static com.epam.reportportal.extension.gitlab.command.GetIssueFieldsCommand.LABELS;
import static com.epam.reportportal.extension.gitlab.command.PredefinedFieldTypes.NAMED_VALUE_FIELDS;
import static com.epam.ta.reportportal.commons.Predicates.isNull;
import static com.epam.ta.reportportal.commons.Predicates.not;
import static com.epam.ta.reportportal.commons.validation.BusinessRule.expect;
import static com.epam.ta.reportportal.ws.model.ErrorType.UNABLE_INTERACT_WITH_INTEGRATION;

import com.epam.reportportal.extension.ProjectMemberCommand;
import com.epam.reportportal.extension.gitlab.client.GitlabClientProvider;
import com.epam.reportportal.extension.gitlab.utils.TicketMapper;
import com.epam.reportportal.extension.util.CommandParamUtils;
import com.epam.reportportal.extension.util.RequestEntityConverter;
import com.epam.reportportal.extension.util.RequestEntityValidator;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.externalsystem.NamedValue;
import com.epam.ta.reportportal.ws.model.externalsystem.PostFormField;
import com.epam.ta.reportportal.ws.model.externalsystem.PostTicketRQ;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class PostTicketCommand extends ProjectMemberCommand<Ticket> {

  private final GitlabClientProvider gitlabClientProvider;
  private final RequestEntityConverter requestEntityConverter;
  private final DescriptionBuilderService descriptionBuilderService;

  public PostTicketCommand(ProjectRepository projectRepository,
      GitlabClientProvider gitlabClientProvider, RequestEntityConverter requestEntityConverter,
      DescriptionBuilderService descriptionBuilderService) {
    super(projectRepository);
    this.gitlabClientProvider = gitlabClientProvider;
    this.requestEntityConverter = requestEntityConverter;
    this.descriptionBuilderService = descriptionBuilderService;
  }

  @Override
  protected Ticket invokeCommand(Integration integration, Map<String, Object> params) {
    PostTicketRQ ticketRQ = requestEntityConverter.getEntity(CommandParamUtils.ENTITY_PARAM, params,
        PostTicketRQ.class);
    RequestEntityValidator.validate(ticketRQ);
    expect(ticketRQ.getFields(), not(isNull())).verify(UNABLE_INTERACT_WITH_INTEGRATION,
        "External System fields set is empty!");
    String project = GitlabProperties.PROJECT.getParam(integration.getParams())
        .orElseThrow(() -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Project key is not specified."));
    Map<String, List<String>> queryParams = handleTicketFields(ticketRQ);
    try {
      return TicketMapper.toTicket(
          gitlabClientProvider.get(integration.getParams()).postIssue(project, queryParams));
    } catch (Exception e) {
      throw new ReportPortalException(ErrorType.BAD_REQUEST_ERROR, e.getMessage());
    }
  }

  private Map<String, List<String>> handleTicketFields(PostTicketRQ ticketRQ) {
    Map<String, List<String>> params = new HashMap<>();
    for (PostFormField field : ticketRQ.getFields()) {
      if ("description".equals(field.getFieldType())) {
        params.put(field.getId(), List.of(field.getValue().get(0).concat("\n")
            .concat(descriptionBuilderService.getDescription(ticketRQ))));
      }
      if (NAMED_VALUE_FIELDS.contains(field.getFieldType())) {
        if (!CollectionUtils.isEmpty(field.getNamedValue())) {
          params.put(field.getId(), Collections.singletonList(field.getNamedValue().stream()
              .filter(Objects::nonNull)
              .map(val -> {
                if (val.getId() != null) {
                  return String.valueOf(val.getId());
                }
                return val.getName();
              })
              .collect(Collectors.joining(","))));
        }
      } else if (!CollectionUtils.isEmpty(field.getValue())) {
        params.put(field.getId(), field.getValue());
      }
    }
    Optional.ofNullable(params.get(ISSUE_TYPE))
        .ifPresent(value -> params.put(ISSUE_TYPE, value.stream()
            .filter(Objects::nonNull)
            .map(String::toLowerCase)
            .collect(Collectors.toList())));
    return params;
  }

  @Override
  public String getName() {
    return "postTicket";
  }
}
