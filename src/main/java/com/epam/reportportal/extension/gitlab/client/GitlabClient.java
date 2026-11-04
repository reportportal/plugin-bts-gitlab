package com.epam.reportportal.extension.gitlab.client;


import com.epam.reportportal.base.infrastructure.persistence.entity.attachment.Attachment;
import com.epam.reportportal.base.infrastructure.rules.exception.ErrorType;
import com.epam.reportportal.base.infrastructure.rules.exception.ReportPortalException;
import com.epam.reportportal.extension.gitlab.dto.EpicDto;
import com.epam.reportportal.extension.gitlab.dto.IssueDto;
import com.epam.reportportal.extension.gitlab.dto.LabelDto;
import com.epam.reportportal.extension.gitlab.dto.MilestoneDto;
import com.epam.reportportal.extension.gitlab.dto.ProjectDto;
import com.epam.reportportal.extension.gitlab.dto.UploadsLinkDto;
import com.epam.reportportal.extension.gitlab.dto.UserDto;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Zsolt Nagyaghy
 */
@Slf4j
public class GitlabClient {

  private static final int DEFAULT_PAGE_SIZE = 100;
  private static final int FIRST_PAGE = 1;
  private static final String QUERY_PAGE = "page";
  private static final String QUERY_PER_PAGE = "per_page";
  private static final String BASE_PATH = "%s/api/v4/projects/%s";
  private static final String GROUP_BASE_PATH = "%s/api/v4/groups/%s";
  private static final String ISSUES_PATH = BASE_PATH + "/issues";
  private static final String SINGLE_ISSUES_PATH = ISSUES_PATH + "/%s";
  private static final String USERS_PATH = BASE_PATH + "/users?search=%s";
  private static final String MILESTONES_PATH = BASE_PATH + "/milestones?search=%s";
  private static final String LABELS_PATH = BASE_PATH + "/labels?search=%s";
  private static final String UPLOADS_PATH = BASE_PATH + "/uploads";
  private static final String EPICS_PATH = GROUP_BASE_PATH + "/epics?search=%s";
  private static final String SPACE = " ";

  private final String baseUrl;
  private final JsonMapper jsonMapper;
  private final RestClient restClient;

  public GitlabClient(String baseUrl, String token, JsonMapper jsonMapper) {
    this(baseUrl, token, jsonMapper, RestClient.builder());
  }

  GitlabClient(String baseUrl, String token, JsonMapper jsonMapper, RestClient.Builder restClientBuilder) {
    this.baseUrl = baseUrl;
    this.jsonMapper = jsonMapper;
    this.restClient = restClientBuilder.defaultHeaders(headers -> {
          headers.setBearerAuth(token);
          headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        })
        .build();
  }

  public ProjectDto getProject(String projectId) {
    String pathUrl = String.format(BASE_PATH, baseUrl, projectId);
    return jsonMapper.convertValue(getEntity(pathUrl), ProjectDto.class);
  }

  public IssueDto getIssue(String issueId, String projectId) {
    String pathUrl = String.format(SINGLE_ISSUES_PATH, baseUrl, projectId, issueId);
    return jsonMapper.convertValue(getEntity(pathUrl), IssueDto.class);
  }

  public List<IssueDto> getIssues(String projectId) {
    String pathUrl = String.format(ISSUES_PATH, baseUrl, projectId);
    return jsonMapper.convertValue(getPages(pathUrl), new TypeReference<>() {
    });
  }

  public IssueDto postIssue(String projectId, Map<String, String> queryParams) {
    String pathUrl = String.format(ISSUES_PATH, baseUrl, projectId);
    Object created = restClient.post()
        .uri(pathUrl)
        .contentType(MediaType.APPLICATION_JSON)
        .body(jsonMapper.writeValueAsString(queryParams))
        .retrieve()
        .body(Object.class);
    return jsonMapper.convertValue(created, IssueDto.class);
  }

  public List<UserDto> searchUsers(String projectId, String term) {
    String pathUrl = String.format(USERS_PATH, baseUrl, projectId, getWrappedCriteria(term));
    return jsonMapper.convertValue(getPages(pathUrl), new TypeReference<>() {
    });
  }

  public List<MilestoneDto> searchMilestones(String projectId, String term) {
    String pathUrl = String.format(MILESTONES_PATH, baseUrl, projectId, getWrappedCriteria(term));
    return jsonMapper.convertValue(getPages(pathUrl), new TypeReference<>() {
    });
  }

  public List<EpicDto> searchEpics(Long groupId, String term) {
    String pathUrl = String.format(EPICS_PATH, baseUrl, groupId, getWrappedCriteria(term));
    return jsonMapper.convertValue(getPages(pathUrl), new TypeReference<>() {
    });
  }

  public List<LabelDto> searchLabels(String project, String term) {
    String pathUrl = String.format(LABELS_PATH, baseUrl, project, term);
    return jsonMapper.convertValue(getPages(pathUrl), new TypeReference<>() {
    });
  }

  private Object getEntity(String url) {
    return restClient.get()
        .uri(url)
        .retrieve()
        .body(Object.class);
  }

  private List<Object> getPages(String path) {
    String url = getUrl(path);
    List<Object> response = new ArrayList<>();
    int page = FIRST_PAGE;
    Object[] body;
    do {
      body = Objects.requireNonNull(
          restClient.get()
              .uri(url, Map.of(QUERY_PAGE, page++))
              .retrieve()
              .body(Object[].class));
      response.addAll(Arrays.asList(body));
    } while (body.length == DEFAULT_PAGE_SIZE);
    return response;
  }

  private String getUrl(String url) {
    return UriComponentsBuilder.fromUriString(url)
        .queryParam(QUERY_PER_PAGE, DEFAULT_PAGE_SIZE)
        .queryParam(QUERY_PAGE, "{" + QUERY_PAGE + "}")
        .build(false)
        .toString();
  }

  public UploadsLinkDto uploadFile(String projectId, Attachment attachment,
      InputStream inputStream) {
    String pathUrl = String.format(UPLOADS_PATH, baseUrl, projectId);
    byte[] byteArray;
    try {
      byteArray = inputStream.readAllBytes();
    } catch (IOException e) {
      throw new ReportPortalException(e.getMessage());
    }
    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentDisposition(ContentDisposition
        .builder("form-data")
        .name("file")
        .filename(attachment.getFileName())
        .build());

    HttpEntity<byte[]> fileEntity = new HttpEntity<>(byteArray, partHeaders);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", fileEntity);

    try {
      return restClient.post()
          .uri(pathUrl)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body)
          .retrieve()
          .body(UploadsLinkDto.class);
    } catch (Exception e) {
      throw new ReportPortalException(ErrorType.UNABLE_INTERACT_WITH_INTEGRATION, "Failed to upload the file");
    }
  }

  private String getWrappedCriteria(String term) {
    return term.contains(SPACE) ? "\"" + term + "\"" : term;
  }

}
