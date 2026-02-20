package com.runeliteagent;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("claudeagent")
public interface ClaudeAgentConfig extends Config
{
    @ConfigSection(
        name = "API Settings",
        description = "Claude API configuration",
        position = 0
    )
    String apiSection = "apiSettings";

    @ConfigItem(
        keyName = "apiKey",
        name = "Claude API Key",
        description = "Your Anthropic Claude API key (starts with sk-ant-)",
        position = 0,
        section = apiSection,
        secret = true
    )
    default String apiKey()
    {
        return "";
    }

    @ConfigItem(
        keyName = "modelId",
        name = "Model",
        description = "Claude model to use",
        position = 1,
        section = apiSection
    )
    default String modelId()
    {
        return "claude-sonnet-4-20250514";
    }

    @ConfigItem(
        keyName = "maxTokens",
        name = "Max Tokens",
        description = "Maximum response tokens",
        position = 2,
        section = apiSection
    )
    default int maxTokens()
    {
        return 4096;
    }
}
