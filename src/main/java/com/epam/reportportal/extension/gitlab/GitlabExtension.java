package com.epam.reportportal.extension.gitlab;

import com.epam.reportportal.base.core.events.domain.PluginUploadedEvent;
import com.epam.reportportal.base.infrastructure.persistence.binary.DataStoreService;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.IntegrationTypeRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.LogRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.ProjectUserRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.TestItemRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationRepository;
import com.epam.reportportal.base.infrastructure.persistence.dao.organization.OrganizationUserRepository;
import com.epam.reportportal.extension.CommonPluginCommand;
import com.epam.reportportal.extension.IntegrationGroupEnum;
import com.epam.reportportal.extension.NamedPluginCommand;
import com.epam.reportportal.extension.PluginCommand;
import com.epam.reportportal.extension.ReportPortalExtensionPoint;
import com.epam.reportportal.extension.bugtracking.BtsActivityPublisher;
import com.epam.reportportal.extension.command.ExtensionCommand;
import com.epam.reportportal.extension.common.IntegrationTypeProperties;
import com.epam.reportportal.extension.gitlab.client.GitlabClientProvider;
import com.epam.reportportal.extension.gitlab.command.DescriptionBuilderService;
import com.epam.reportportal.extension.gitlab.command.GetIssueCommand;
import com.epam.reportportal.extension.gitlab.command.GetIssueFieldsCommand;
import com.epam.reportportal.extension.gitlab.command.GetIssueTypesCommand;
import com.epam.reportportal.extension.gitlab.command.GetIssuesCommand;
import com.epam.reportportal.extension.gitlab.command.PostTicketCommand;
import com.epam.reportportal.extension.gitlab.command.RetrieveCreationParamsCommand;
import com.epam.reportportal.extension.gitlab.command.RetrieveUpdateParamsCommand;
import com.epam.reportportal.extension.gitlab.command.SearchEpicsCommand;
import com.epam.reportportal.extension.gitlab.command.SearchLabelsCommand;
import com.epam.reportportal.extension.gitlab.command.SearchMilestonesCommand;
import com.epam.reportportal.extension.gitlab.command.SearchUsersCommand;
import com.epam.reportportal.extension.gitlab.command.TestConnectionCommand;
import com.epam.reportportal.extension.gitlab.event.plugin.PluginLoadedEventListener;
import com.epam.reportportal.extension.gitlab.info.impl.PluginInfoProviderImpl;
import com.epam.reportportal.extension.gitlab.utils.MemoizingSupplier;
import com.epam.reportportal.extension.util.RequestEntityConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jasypt.util.text.BasicTextEncryptor;
import org.pf4j.Extension;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * @author Zsolt Nagyaghy
 */
@Extension
public class GitlabExtension implements ReportPortalExtensionPoint, DisposableBean {

  public static final String BINARY_DATA_PROPERTIES_FILE_ID = "binary-data.properties";
  private static final String PLUGIN_ID = "GitLab";
  private static final String DOCUMENTATION_LINK_FIELD = "documentationLink";
  private static final String DOCUMENTATION_LINK = "https://reportportal.io/docs/plugins/GitLab/";
  private final String resourcesDir;
  private final Supplier<RequestEntityConverter> requestEntityConverterSupplier;
  private final Supplier<ApplicationListener<PluginUploadedEvent>> pluginLoadedListenerSupplier;
  private final Supplier<GitlabClientProvider> gitlabClientProviderSupplier;
  private final Supplier<DescriptionBuilderService> descriptionBuilderServiceSupplier;
  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private IntegrationTypeRepository integrationTypeRepository;
  @Autowired
  private IntegrationRepository integrationRepository;
  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private OrganizationUserRepository organizationUserRepository;
  @Autowired
  private OrganizationRepository organizationRepository;
  @Autowired
  private ProjectUserRepository projectUserRepository;

  private final Supplier<Map<String, ExtensionCommand<?>>> pluginCommandMapping = new MemoizingSupplier<>(
      this::getIntegrationExtensionCommands);
  private final Supplier<Map<String, ExtensionCommand<?>>> commonCommandMapping = new MemoizingSupplier<>(
      this::getCommonExtensionCommands);
  @Autowired
  private LogRepository logRepository;
  @Autowired
  private TestItemRepository testItemRepository;
  @Autowired
  private BasicTextEncryptor textEncryptor;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  @Qualifier("attachmentDataStoreService")
  private DataStoreService dataStoreService;
  @Autowired
  private BtsActivityPublisher btsActivityPublisher;

  public GitlabExtension(Map<String, Object> initParams) {
    resourcesDir = IntegrationTypeProperties.RESOURCES_DIRECTORY.getValue(initParams)
        .map(String::valueOf).orElse("");

    pluginLoadedListenerSupplier = new MemoizingSupplier<>(
        () -> new PluginLoadedEventListener(PLUGIN_ID, integrationTypeRepository, integrationRepository,
            new PluginInfoProviderImpl(resourcesDir, BINARY_DATA_PROPERTIES_FILE_ID)
        ));

    gitlabClientProviderSupplier = new MemoizingSupplier<>(
        () -> new GitlabClientProvider(textEncryptor, objectMapper));
    requestEntityConverterSupplier = new MemoizingSupplier<>(() -> new RequestEntityConverter(objectMapper));
    descriptionBuilderServiceSupplier = new MemoizingSupplier<>(
        () -> new DescriptionBuilderService(logRepository, testItemRepository, dataStoreService));
  }

  @Override
  public Map<String, ?> getPluginParams() {
    Map<String, Object> params = new HashMap<>();
    params.put(ALLOWED_COMMANDS, new ArrayList<>(pluginCommandMapping.get().keySet()));
    params.put(DOCUMENTATION_LINK_FIELD, DOCUMENTATION_LINK);
    params.put(COMMON_COMMANDS, new ArrayList<>(commonCommandMapping.get().keySet()));
    return params;
  }

  @Override
  public PluginCommand<?> getIntegrationCommand(String commandName) {
    return null;
  }

  @Override
  public CommonPluginCommand<?> getCommonCommand(String commandName) {
    return null;
  }

  @Override
  public IntegrationGroupEnum getIntegrationGroup() {
    return IntegrationGroupEnum.BTS;
  }

  @PostConstruct
  public void createIntegration() {
    initListeners();
  }

  private void initListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class
    );
    applicationEventMulticaster.addApplicationListener(pluginLoadedListenerSupplier.get());
  }

  @Override
  public void destroy() {
    removeListeners();
  }

  private void removeListeners() {
    ApplicationEventMulticaster applicationEventMulticaster = applicationContext.getBean(
        AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
        ApplicationEventMulticaster.class
    );
    applicationEventMulticaster.removeApplicationListener(pluginLoadedListenerSupplier.get());
  }

  @Override
  public Map<String, ExtensionCommand<?>> getCommonExtensionCommands() {
    List<ExtensionCommand<?>> commands = new ArrayList<>();
    commands.add(new RetrieveCreationParamsCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new RetrieveUpdateParamsCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new GetIssueCommand(gitlabClientProviderSupplier.get(), integrationRepository,
        projectRepository, organizationUserRepository, organizationRepository,
        projectUserRepository));
    return commands.stream().collect(Collectors.toMap(NamedPluginCommand::getName, it -> it));
  }

  @Override
  public Map<String, ExtensionCommand<?>> getIntegrationExtensionCommands() {
    List<ExtensionCommand<?>> commands = new ArrayList<>();
    commands.add(new TestConnectionCommand(gitlabClientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new GetIssuesCommand(gitlabClientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new SearchUsersCommand(gitlabClientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new SearchMilestonesCommand(gitlabClientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new SearchEpicsCommand(gitlabClientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new SearchLabelsCommand(gitlabClientProviderSupplier.get(), projectRepository,
        organizationUserRepository, organizationRepository, projectUserRepository));
    commands.add(new GetIssueTypesCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new GetIssueFieldsCommand(projectRepository, organizationUserRepository,
        organizationRepository, projectUserRepository));
    commands.add(new PostTicketCommand(projectRepository, gitlabClientProviderSupplier.get(),
        requestEntityConverterSupplier.get(), descriptionBuilderServiceSupplier.get(), organizationUserRepository,
        organizationRepository, projectUserRepository, btsActivityPublisher));
    return commands.stream().collect(Collectors.toMap(NamedPluginCommand::getName, it -> it));
  }
}
