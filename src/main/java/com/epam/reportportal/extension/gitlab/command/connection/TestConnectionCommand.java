/*
 * Copyright 2021 EPAM Systems
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
package com.epam.reportportal.extension.gitlab.command.connection;

import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.gitlab.command.utils.GitlabProperties;
import com.epam.reportportal.extension.gitlab.rest.client.GitlabClient;
import com.epam.reportportal.extension.gitlab.rest.client.GitlabClientProvider;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.entity.integration.IntegrationParams;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.ErrorType;
import org.gitlab4j.api.models.Project;

import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.hibernate.bytecode.BytecodeLogger.LOGGER;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class TestConnectionCommand implements PluginCommand<Boolean> {

    private final GitlabClientProvider gitlabClientProvider;

    public TestConnectionCommand(GitlabClientProvider gitlabClientProvider) {
        this.gitlabClientProvider = gitlabClientProvider;
    }

    @Override
    public String getName() {
        return "testConnection";
    }

    @Override
    public Boolean executeCommand(Integration integration, Map<String, Object> params) {
        IntegrationParams integrationParams = ofNullable(integration.getParams()).orElseThrow(() -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
                "Integration params are not specified."
        ));

        String project = GitlabProperties.PROJECT.getParam(integrationParams)
                .orElseThrow(() -> new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "Project key is not specified."));

        try {
            GitlabClient restClient = gitlabClientProvider.apiClientFactory(integrationParams);
            Project project1 = restClient.getProject(project);
            boolean b = project1 != null;
            if (b) {
                System.out.println("=============================== SUCCESS");
                System.out.println("=============================== " + project1);
            } else {
                System.out.println("=============================== FAILURE");
            }
            return b;
        } catch (Exception e) {
            LOGGER.error("Unable to connect to GitLab: " + e.getMessage(), e);
            throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION,
                    String.format("Unable to connect to GitLab. Message: %s", e.getMessage()),
                    e
            );
        }
    }
}
