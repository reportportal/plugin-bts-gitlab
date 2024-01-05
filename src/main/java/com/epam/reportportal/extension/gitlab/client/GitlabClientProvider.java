package com.epam.reportportal.extension.gitlab.client;

import com.epam.reportportal.extension.gitlab.command.GitlabProperties;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.ErrorType;
import org.jasypt.util.text.BasicTextEncryptor;

/**
 * @author Zsolt Nagyaghy
 */
public class GitlabClientProvider {

  protected BasicTextEncryptor textEncryptor;

  public GitlabClientProvider(BasicTextEncryptor textEncryptor) {
    this.textEncryptor = textEncryptor;
  }

  public GitlabClient get(IntegrationParams integrationParams) {
    String credentials = textEncryptor.decrypt(
        GitlabProperties.API_TOKEN.getParam(integrationParams)
            .orElseThrow(() -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
                "Access token is not specified.")));
    String url = GitlabProperties.URL.getParam(integrationParams)
        .orElseThrow(() -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
            "Url to the GitLab is not specified."
        ));
    return new GitlabClient(url, credentials);
  }
}
