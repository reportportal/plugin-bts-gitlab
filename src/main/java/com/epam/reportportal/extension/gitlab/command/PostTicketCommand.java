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
import com.epam.ta.reportportal.ws.model.externalsystem.PostFormField;
import com.epam.ta.reportportal.ws.model.externalsystem.PostTicketRQ;
import com.epam.ta.reportportal.ws.model.externalsystem.Ticket;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class PostTicketCommand extends ProjectMemberCommand<Ticket> {

  private final GitlabClientProvider gitlabClientProvider;
  private final RequestEntityConverter requestEntityConverter;

  public PostTicketCommand(ProjectRepository projectRepository,
      GitlabClientProvider gitlabClientProvider, RequestEntityConverter requestEntityConverter) {
    super(projectRepository);
    this.gitlabClientProvider = gitlabClientProvider;
    this.requestEntityConverter = requestEntityConverter;
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

    Map<String, List<String>> queryParams = ticketRQ.getFields().stream()
        .filter(form -> form.getValue() != null && !form.getValue().isEmpty())
        .collect(Collectors.toMap(PostFormField::getId, PostFormField::getValue));

    ticketRQ.getFields().stream().filter(field -> !CollectionUtils.isEmpty(field.getNamedValue()))
        .forEach(field -> queryParams.put(field.getId(),
            field.getNamedValue().stream().map(it -> String.valueOf(it.getId())).collect(
                Collectors.toList())));

    try {
      return TicketMapper.toTicket(
          gitlabClientProvider.get(integration.getParams()).postIssue(project, queryParams));
    } catch (Exception e) {
      throw new ReportPortalException(ErrorType.BAD_REQUEST_ERROR, e.getMessage());
    }
  }

  @Override
  public String getName() {
    return "postTicket";
  }
}