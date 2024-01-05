package com.epam.reportportal.extension.gitlab.client;


import com.epam.reportportal.extension.gitlab.dto.EpicDto;
import com.epam.reportportal.extension.gitlab.dto.IssueDto;
import com.epam.reportportal.extension.gitlab.dto.LabelDto;
import com.epam.reportportal.extension.gitlab.dto.MilestoneDto;
import com.epam.reportportal.extension.gitlab.dto.ProjectDto;
import com.epam.reportportal.extension.gitlab.dto.UploadsLinkDto;
import com.epam.reportportal.extension.gitlab.dto.UserDto;
import com.epam.reportportal.extension.gitlab.utils.GitlabObjectMapperProvider;
import com.epam.ta.reportportal.entity.attachment.Attachment;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jooq.tools.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Zsolt Nagyaghy
 */
public class GitlabClient {

  private static final Logger logger = LoggerFactory.getLogger(GitlabClient.class);

  private static final Integer DEFAULT_PAGE_SIZE = 100;
  private static final Integer FIRST_PAGE = 1;
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
  private static final Map<String, List<String>> pageParams = Map.of(QUERY_PER_PAGE,
      List.of(DEFAULT_PAGE_SIZE.toString()), QUERY_PAGE, List.of("{page}"));

  private static final String SPACE = " ";

  private final String baseUrl;
  private final String token;
  ObjectMapper objectMapper = new GitlabObjectMapperProvider().getObjectMapper();

  public GitlabClient(String baseUrl, String token) {
    this.baseUrl = baseUrl;
    this.token = token;
  }

  public ProjectDto getProject(String projectId) {
    String pathUrl = String.format(BASE_PATH, baseUrl, projectId);
    Object singleEntity = singleEntityRequests(pathUrl, Map.of(), HttpMethod.GET);
    return objectMapper.convertValue(singleEntity, ProjectDto.class);
  }

  public IssueDto getIssue(String issueId, String projectId) {
    String pathUrl = String.format(SINGLE_ISSUES_PATH, baseUrl, projectId, issueId);
    Object singleEntity = singleEntityRequests(pathUrl, Map.of(), HttpMethod.GET);
    return objectMapper.convertValue(singleEntity, IssueDto.class);
  }

  public List<IssueDto> getIssues(String projectId) {
    List<IssueDto> response = new LinkedList<>();
    HashMap<String, List<String>> queryParams = new HashMap<>(pageParams);

    String pathUrl = String.format(ISSUES_PATH, baseUrl, projectId);
    getLists(response, pathUrl, queryParams);
    return objectMapper.convertValue(response, new TypeReference<>() {
    });
  }

  public IssueDto postIssue(String projectId, Map<String, String> queryParams) {
    String pathUrl = String.format(ISSUES_PATH, baseUrl, projectId);
    HttpHeaders httpHeaders = getHttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    JSONObject personJsonObject = new JSONObject();
    personJsonObject.putAll(queryParams);
    HttpEntity<String> request = new HttpEntity<>(personJsonObject.toString(), httpHeaders);
    return objectMapper.convertValue(
        exchangeRequest(request, new RestTemplate(), pathUrl, HttpMethod.POST), IssueDto.class);
  }

  public List<UserDto> searchUsers(String projectId, String term) {
    String wrappedSearchCriteria = getWrappedCriteria(term);
    String pathUrl = String.format(USERS_PATH, baseUrl, projectId, wrappedSearchCriteria);
    List<Object> response = new ArrayList<>();
    getLists(response, pathUrl, new HashMap<>(pageParams));
    return objectMapper.convertValue(response, new TypeReference<>() {
    });
  }

  public List<MilestoneDto> searchMilestones(String projectId, String term) {
    String wrappedSearchCriteria = getWrappedCriteria(term);
    String pathUrl = String.format(MILESTONES_PATH, baseUrl, projectId, wrappedSearchCriteria);
    List<Object> response = new ArrayList<>();
    getLists(response, pathUrl, new HashMap<>(pageParams));
    return objectMapper.convertValue(response, new TypeReference<>() {
    });
  }

  public List<EpicDto> searchEpics(Long groupId, String term) {
    String wrappedSearchCriteria = getWrappedCriteria(term);
    String pathUrl = String.format(EPICS_PATH, baseUrl, groupId, wrappedSearchCriteria);
    List<Object> response = new ArrayList<>();
    getLists(response, pathUrl, new HashMap<>(pageParams));
    return objectMapper.convertValue(response, new TypeReference<>() {
    });
  }

  public List<LabelDto> searchLabels(String project, String term) {
    String pathUrl = String.format(LABELS_PATH, baseUrl, project, term);
    List<Object> response = new ArrayList<>();
    getLists(response, pathUrl, new HashMap<>(pageParams));
    return objectMapper.convertValue(response, new TypeReference<>() {
    });
  }

  private <T> T singleEntityRequests(String path, Map<String, List<String>> queryParams,
      HttpMethod method) {
    HttpHeaders headers = getHttpHeaders();
    HttpEntity<String> entity = new HttpEntity<>(null, headers);
    RestTemplate restTemplate = new RestTemplate();
    String url = getUrl(path, queryParams);
    return exchangeRequest(entity, restTemplate, url, method);
  }

  private <T> void getLists(List<T> response, String path, Map<String, List<String>> queryParams) {
    HttpHeaders headers = getHttpHeaders();
    HttpEntity<String> entity = new HttpEntity<>(null, headers);
    RestTemplate restTemplate = new RestTemplate();
    String url = getUrl(path, queryParams);
    exchangeRequest(FIRST_PAGE, response, entity, restTemplate, url);
  }

  private <T> T exchangeRequest(HttpEntity<String> entity, RestTemplate restTemplate, String url,
      HttpMethod httpMethod) {
    ResponseEntity<T> exchange = restTemplate.exchange(url, httpMethod, entity,
        new ParameterizedTypeReference<>() {
        });
    return exchange.getBody();
  }

  private <T> void exchangeRequest(int page, List<T> response, HttpEntity<String> entity,
      RestTemplate restTemplate, String url) {
    ResponseEntity<T[]> exchange = restTemplate.exchange(url, HttpMethod.GET, entity,
        new ParameterizedTypeReference<>() {
        }, Map.of(QUERY_PAGE, page));

    T[] body = exchange.getBody();
    response.addAll(Arrays.asList(Objects.requireNonNull(body)));

    if (body.length == DEFAULT_PAGE_SIZE) {
      exchangeRequest(++page, response, entity, restTemplate, url);
    }
  }

  private String getUrl(String url, Map<String, List<String>> queryParams) {
    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(url);
    queryParams.keySet().forEach(key -> uriComponentsBuilder.queryParam(key, queryParams.get(key)));
    return uriComponentsBuilder.build(false).toString();
  }

  private HttpHeaders getHttpHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    return headers;
  }

  public UploadsLinkDto uploadFile(String projectId, Attachment attachment,
      InputStream inputStream) {
    String pathUrl = String.format(UPLOADS_PATH, baseUrl, projectId);
    HttpHeaders headers = getHttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    byte[] byteArray;
    try {
      byteArray = inputStream.readAllBytes();
    } catch (IOException e) {
      throw new ReportPortalException(e.getMessage());
    }
    MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();

    ContentDisposition contentDisposition = ContentDisposition
        .builder("form-data")
        .name("file")
        .filename(attachment.getFileName())
        .build();

    fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

    HttpEntity<byte[]> fileEntity = new HttpEntity<>(byteArray, fileMap);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", fileEntity);

    HttpEntity<MultiValueMap<String, Object>> requestEntity =
        new HttpEntity<>(body, headers);
    try {
      ResponseEntity<UploadsLinkDto> response = new RestTemplate().exchange(
          pathUrl,
          HttpMethod.POST,
          requestEntity,
          UploadsLinkDto.class);
      return response.getBody();
    } catch (Exception e) {
      throw new ReportPortalException(e.getMessage());
    }
  }

  private String getWrappedCriteria(String term) {
    return term.contains(SPACE) ? "\"" + term + "\"" : term;
  }

}
