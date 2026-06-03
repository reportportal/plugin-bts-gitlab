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

import static java.util.Optional.ofNullable;

import com.epam.reportportal.api.model.PluginCommandRQ;
import com.epam.reportportal.base.infrastructure.model.externalsystem.Ticket;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepositoryCustom;
import com.epam.reportportal.base.infrastructure.persistence.entity.integration.Integration;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.command.AbstractExtensionCommand;
import com.epam.reportportal.extension.gitlab.client.GitlabClientProvider;
import com.epam.reportportal.extension.gitlab.utils.TicketMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * @author <a href="mailto:andrei_piankouski@epam.com">Andrei Piankouski</a>
 */
@Slf4j
public class GetIssueCommand extends AbstractExtensionCommand<Ticket> {

  private static final String PROJECT_ID = "projectId";

  private final GitlabClientProvider gitlabClientProvider;
  private final IntegrationRepository integrationRepository;

  public GetIssueCommand(GitlabClientProvider gitlabClientProvider,
      IntegrationRepository integrationRepository, ProjectRepository projectRepository,
      OrganizationRepositoryCustom organizationRepository) {
    super(projectRepository, organizationRepository);
    this.gitlabClientProvider = gitlabClientProvider;
    this.integrationRepository = integrationRepository;
  }

  @Override
  public Ticket executeCommand(PluginCommandRQ pluginCommandRq) {
    var params = pluginCommandRq.getArguments();
    final Long projectId = (Long) ofNullable(params.get(PROJECT_ID)).orElseThrow(
        () -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
            PROJECT_ID + " must be provided"
        ));
    String btsProject = GitlabProperties.PROJECT.getParam(params).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Bts Project id is not specified."
        ));
    String issueId = GitlabProperties.TICKET_ID.getParam(params).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Issue id is not specified."
        ));
    final String btsUrl = GitlabProperties.URL.getParam(params).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Url is not specified."
        ));
    final Integration integration =
        integrationRepository.findProjectBtsByUrlAndLinkedProject(btsUrl, btsProject, projectId)
            .orElseGet(
                () -> integrationRepository.findGlobalBtsByUrlAndLinkedProject(btsUrl, btsProject)
                    .orElseThrow(() -> new ReportPortalException(
                        ErrorType.BAD_REQUEST_ERROR,
                        "Integration with provided url and project isn't found"
                    )));
    try {
      return TicketMapper.toTicket(
          gitlabClientProvider.get(integration.getParams()).getIssue(issueId, btsProject));
    } catch (Exception e) {
      log.error("Issue not found: {}", e.getMessage(), e);
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
          "Failed to retrieve the Gitlab ticket");
    }
  }

  @Override
  public String getName() {
    return "getIssue";
  }
}
