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

import static com.epam.reportportal.extension.gitlab.command.GetIssueTypesCommand.ISSUE;
import static com.epam.reportportal.extension.gitlab.command.PredefinedFieldTypes.CREATABLE_MULTI_AUTOCOMPLETE;
import static com.epam.reportportal.extension.gitlab.command.PredefinedFieldTypes.MULTI_AUTOCOMPLETE;

import com.epam.reportportal.extension.ProjectMemberCommand;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.entity.integration.Integration;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.externalsystem.AllowedValue;
import com.epam.ta.reportportal.ws.model.externalsystem.PostFormField;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author <a href="mailto:pavel_bortnik@epam.com">Pavel Bortnik</a>
 */
public class GetIssueFieldsCommand extends ProjectMemberCommand<List<PostFormField>> {

  public static final String ISSUE_TYPE = "issue_type";
  public static final String ISSUE_TYPE_PARAM = "issueType";
  public static final String LABELS = "labels";

  private static final String PAID_DESCRIPTION = "Available only for paid Enterprise version of GitLab";
  private static final String ASSIGNEE_DESCRIPTION = "Note that the free version of GitLab allows only one assignee";

  public GetIssueFieldsCommand(ProjectRepository projectRepository) {
    super(projectRepository);
  }

  @Override
  protected List<PostFormField> invokeCommand(Integration integration, Map<String, Object> params) {
    String issueTypeParam = Optional.ofNullable(params.get(ISSUE_TYPE_PARAM))
        .map(it -> (String) it)
        .orElseThrow(() -> new ReportPortalException(ErrorType.BAD_REQUEST_ERROR,
            "Issue type is not provided"));
    List<PostFormField> result = Lists.newArrayList(
        PostFormField.builder().id("title").fieldName("Title").fieldType("string").isRequired(true)
            .build(),
        PostFormField.builder().id("description").fieldName("Description").fieldType("string")
            .build(),
        PostFormField.builder().id(ISSUE_TYPE).fieldName("Issue type").fieldType("issuetype")
            .isRequired(true)
            .definedValues(List.of(
                new AllowedValue("issue", "issue"),
                new AllowedValue("incident", "incident")))
            .build(),
        PostFormField.builder().id("confidential").fieldName("Confidential").fieldType("string")
            .definedValues(List.of(
                new AllowedValue("false", "No"),
                new AllowedValue("true", "Yes")))
            .build(),
        PostFormField.builder().id("assignee_ids").fieldName("Assignee(s)")
            .fieldType(MULTI_AUTOCOMPLETE).commandName("searchUsers")
            .description(ASSIGNEE_DESCRIPTION).build(),
        PostFormField.builder().id("due_date").fieldName("Due Date").fieldType("string").build(),
        PostFormField.builder().id(LABELS).fieldName("Labels")
            .fieldType(CREATABLE_MULTI_AUTOCOMPLETE)
            .commandName("searchLabels").build(),
        PostFormField.builder().id("milestone_id").fieldName("Milestone").fieldType("autocomplete")
            .commandName("searchMilestones").build()
    );

    if (ISSUE.equalsIgnoreCase(issueTypeParam)) {
      result.add(PostFormField.builder().id("epic_id").fieldName("Epic").fieldType("autocomplete")
          .commandName("searchEpics").description(PAID_DESCRIPTION).build());
      result.add(PostFormField.builder().id("weight").fieldName("Weight").fieldType("integer")
          .description(PAID_DESCRIPTION).build());
    }
    return result;
  }

  @Override
  public String getName() {
    return "getIssueFields";
  }
}
