package com.runeliteagent;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
    name = "Claude Agent",
    description = "AI-powered assistant that can control RuneLite and answer OSRS questions using Claude",
    tags = {"ai", "claude", "agent", "assistant", "chat", "osrs", "wiki"}
)
public class ClaudeAgentPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClaudeAgentConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    @Inject
    private PluginManager pluginManager;

    private ClaudeAgentPanel panel;
    private NavigationButton navButton;
    private RuneLiteController runeLiteController;
    private OsrsWikiClient wikiClient;
    private ClaudeApiClient apiClient;
    private AgentOrchestrator orchestrator;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Claude Agent plugin started");

        runeLiteController = new RuneLiteController(configManager, pluginManager, client);
        wikiClient = new OsrsWikiClient();
        apiClient = new ClaudeApiClient(config);
        orchestrator = new AgentOrchestrator(apiClient, runeLiteController, wikiClient, config);

        panel = new ClaudeAgentPanel(orchestrator, config);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        navButton = NavigationButton.builder()
            .tooltip("Claude Agent")
            .icon(icon)
            .priority(10)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Claude Agent plugin stopped");
        clientToolbar.removeNavigation(navButton);
        if (orchestrator != null)
        {
            orchestrator.shutdown();
        }
        if (wikiClient != null)
        {
            wikiClient.shutdown();
        }
    }

    @Provides
    ClaudeAgentConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ClaudeAgentConfig.class);
    }
}
