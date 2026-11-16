package com.epam.reportportal.extension.gitlab.client;

import com.epam.reportportal.base.infrastructure.persistence.entity.integration.IntegrationParams;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.gitlab.command.GitlabProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jasypt.util.text.BasicTextEncryptor;

/**
 * @author Zsolt Nagyaghy
 */
@RequiredArgsConstructor
public class GitlabClientProvider {

  private final BasicTextEncryptor textEncryptor;
  private final ObjectMapper objectMapper;

  public GitlabClient get(IntegrationParams integrationParams) {
    String credentials = textEncryptor.decrypt(
        GitlabProperties.API_TOKEN.getParam(integrationParams).orElseThrow(
            () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
                "Access token is not specified."
            )));
    String url = GitlabProperties.URL.getParam(integrationParams).orElseThrow(
        () -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Url to the GitLab is not specified."
        ));
    return new GitlabClient(url, credentials, objectMapper);
  }
}
