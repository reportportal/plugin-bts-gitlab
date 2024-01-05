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

package com.epam.reportportal.extension.gitlab.command;

import static com.epam.ta.reportportal.commons.EntityUtils.TO_DATE;
import static java.util.Optional.ofNullable;

import com.epam.reportportal.extension.gitlab.client.GitlabClient;
import com.epam.reportportal.extension.gitlab.dto.UploadsLinkDto;
import com.epam.ta.reportportal.binary.DataStoreService;
import com.epam.ta.reportportal.dao.LogRepository;
import com.epam.ta.reportportal.dao.TestItemRepository;
import com.epam.ta.reportportal.entity.attachment.Attachment;
import com.epam.ta.reportportal.entity.item.TestItem;
import com.epam.ta.reportportal.entity.log.Log;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.externalsystem.PostTicketRQ;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide functionality for building ticket description
 *
 * @author Aliaksei_Makayed
 * @author Dzmitry_Kavalets
 */
public class DescriptionBuilderService {

  public static final String BACK_LINK_HEADER = "### **Back link to Report Portal:**";
  public static final String BACK_LINK_PATTERN = "[Link to defect](%s)%n";
  public static final String COMMENTS_HEADER = "### **Test Item comments:**";
  public static final String CODE = "`";
  private static final Logger LOGGER = LoggerFactory.getLogger(DescriptionBuilderService.class);
  private static final String IMAGE_CONTENT = "image";
  private static final String IMAGE_HEIGHT_TEMPLATE = "|height=366!";

  private final LogRepository logRepository;
  private final TestItemRepository itemRepository;
  private final DataStoreService dataStoreService;
  private final DateFormat dateFormat;
  private final MimeTypes mimeRepository;
  private GitlabClient gitlabClient;
  private String projectId;

  public DescriptionBuilderService(LogRepository logRepository, TestItemRepository itemRepository,
      DataStoreService dataStoreService) {
    this.dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    this.logRepository = logRepository;
    this.itemRepository = itemRepository;
    this.mimeRepository = TikaConfig.getDefaultConfig().getMimeRepository();
    this.dataStoreService = dataStoreService;
  }

  /**
   * Generate ticket description using logs of specified test item.
   *
   * @param ticketRQ
   * @return
   */
  public String getDescription(PostTicketRQ ticketRQ, GitlabClient gitlabClient,
      String gitlabProjectId) {
    this.gitlabClient = gitlabClient;
    this.projectId = gitlabProjectId;
    if (MapUtils.isEmpty(ticketRQ.getBackLinks())) {
      return "";
    }
    StringBuilder descriptionBuilder = new StringBuilder();

    TestItem item = itemRepository.findById(ticketRQ.getTestItemId())
        .orElseThrow(() -> new ReportPortalException(ErrorType.TEST_ITEM_NOT_FOUND,
            ticketRQ.getTestItemId()));
    ticketRQ.getBackLinks().keySet().forEach(
        backLinkId -> updateDescriptionBuilder(descriptionBuilder, ticketRQ, backLinkId, item));
    return descriptionBuilder.toString();
  }

  private void updateDescriptionBuilder(StringBuilder descriptionBuilder, PostTicketRQ ticketRQ,
      Long backLinkId, TestItem item) {
    if (StringUtils.isNotBlank(ticketRQ.getBackLinks().get(backLinkId))) {
      descriptionBuilder.append(BACK_LINK_HEADER)
          .append("\n\n")
          .append(" - ")
          .append(String.format(BACK_LINK_PATTERN, ticketRQ.getBackLinks().get(backLinkId)))
          .append("\n\n");
    }
    // For single test-item only
    if (ticketRQ.getIsIncludeComments()) {
      if (StringUtils.isNotBlank(ticketRQ.getBackLinks().get(backLinkId))) {
        // If test-item contains any comments, then add it for bts
        // comments section
        ofNullable(item.getItemResults()).flatMap(result -> ofNullable(result.getIssue()))
            .ifPresent(issue -> {
              if (StringUtils.isNotBlank(issue.getIssueDescription())) {
                descriptionBuilder.append(COMMENTS_HEADER).append("\n\n")
                    .append(issue.getIssueDescription()).append("\n\n");
              }
            });
      }
    }
    updateWithLogsInfo(descriptionBuilder, backLinkId, ticketRQ);
  }

  private StringBuilder updateWithLogsInfo(StringBuilder descriptionBuilder, Long backLinkId,
      PostTicketRQ ticketRQ) {
    itemRepository.findById(backLinkId)
        .ifPresent(item -> ofNullable(item.getLaunchId()).ifPresent(launchId -> {
          List<Log> logs = logRepository.findAllUnderTestItemByLaunchIdAndTestItemIdsWithLimit(
              launchId,
              Collections.singletonList(item.getItemId()),
              ticketRQ.getNumberOfLogs()
          );
          if (CollectionUtils.isNotEmpty(logs) && (ticketRQ.getIsIncludeLogs()
              || ticketRQ.getIsIncludeScreenshots())) {
            descriptionBuilder.append("### **Test execution log:** \n\n");
            logs.forEach(log -> updateWithLog(descriptionBuilder,
                log,
                ticketRQ.getIsIncludeLogs(),
                ticketRQ.getIsIncludeScreenshots()
            ));
          }
        }));
    return descriptionBuilder;
  }

  private void updateWithLog(StringBuilder descriptionBuilder, Log log, boolean includeLog,
      boolean includeScreenshot) {
    if (includeLog) {
      descriptionBuilder.append(getFormattedMessage(log));
    }

    if (includeScreenshot) {
      ofNullable(log.getAttachment()).ifPresent(
          attachment -> addAttachment(descriptionBuilder, attachment));
    }
  }

  private String getFormattedMessage(Log log) {
    StringBuilder messageBuilder = new StringBuilder();
    messageBuilder.append(CODE);
    ofNullable(log.getLogTime()).ifPresent(logTime -> messageBuilder.append(" Time: ")
        .append(dateFormat.format(TO_DATE.apply(logTime)))
        .append(", "));
    ofNullable(log.getLogLevel()).ifPresent(
        logLevel -> messageBuilder.append("Level: ").append(logLevel).append(", "));
    messageBuilder.append("Log: ").append(log.getLogMessage()).append(CODE).append("\n\n");
    return messageBuilder.toString();
  }

  private void addAttachment(StringBuilder descriptionBuilder, Attachment attachment) {
    if (StringUtils.isNotBlank(attachment.getContentType()) && StringUtils.isNotBlank(
        attachment.getFileId())) {
      Optional<InputStream> load = dataStoreService.load(attachment.getFileId());
      if (load.isPresent()) {
        try (InputStream fileInputStream = load.get()) {
          UploadsLinkDto link = gitlabClient.uploadFile(projectId, attachment, fileInputStream);
          descriptionBuilder.append(link.getMarkdown()).append("\n\n");
        } catch (IOException e) {
          throw new ReportPortalException(e.getMessage());
        }
      }
    }
  }
}