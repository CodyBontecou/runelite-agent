package com.runeliteagent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

@Slf4j
public class RuneLiteController
{
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final Client client;

    public RuneLiteController(ConfigManager configManager, PluginManager pluginManager, Client client)
    {
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.client = client;
    }

    /**
     * List all installed plugins and their enabled/disabled status.
     */
    public String listPlugins()
    {
        List<String> lines = new ArrayList<>();
        for (Plugin plugin : pluginManager.getPlugins())
        {
            String name = plugin.getName();
            boolean enabled = pluginManager.isPluginEnabled(plugin);
            lines.add(String.format("- %s [%s]", name, enabled ? "ENABLED" : "DISABLED"));
        }
        lines.sort(String::compareToIgnoreCase);
        return "Installed Plugins:\n" + String.join("\n", lines);
    }

    /**
     * Enable a plugin by name (case-insensitive partial match).
     */
    public String enablePlugin(String pluginName)
    {
        Plugin plugin = findPlugin(pluginName);
        if (plugin == null)
        {
            return "Plugin not found: " + pluginName + ". Use list_plugins to see available plugins.";
        }
        if (pluginManager.isPluginEnabled(plugin))
        {
            return "Plugin '" + plugin.getName() + "' is already enabled.";
        }
        try
        {
            pluginManager.setPluginEnabled(plugin, true);
            pluginManager.startPlugin(plugin);
            return "Successfully enabled plugin: " + plugin.getName();
        }
        catch (Exception e)
        {
            log.error("Failed to enable plugin: {}", pluginName, e);
            return "Failed to enable plugin '" + plugin.getName() + "': " + e.getMessage();
        }
    }

    /**
     * Disable a plugin by name (case-insensitive partial match).
     */
    public String disablePlugin(String pluginName)
    {
        Plugin plugin = findPlugin(pluginName);
        if (plugin == null)
        {
            return "Plugin not found: " + pluginName + ". Use list_plugins to see available plugins.";
        }
        if (!pluginManager.isPluginEnabled(plugin))
        {
            return "Plugin '" + plugin.getName() + "' is already disabled.";
        }
        try
        {
            pluginManager.setPluginEnabled(plugin, false);
            pluginManager.stopPlugin(plugin);
            return "Successfully disabled plugin: " + plugin.getName();
        }
        catch (Exception e)
        {
            log.error("Failed to disable plugin: {}", pluginName, e);
            return "Failed to disable plugin '" + plugin.getName() + "': " + e.getMessage();
        }
    }

    /**
     * Get the current value of a plugin config key.
     */
    public String getConfigValue(String group, String key)
    {
        String value = configManager.getConfiguration(group, key);
        if (value == null)
        {
            return "No config value found for group='" + group + "', key='" + key + "'.";
        }
        return "Config [" + group + "." + key + "] = " + value;
    }

    /**
     * Set a plugin config value.
     */
    public String setConfigValue(String group, String key, String value)
    {
        try
        {
            configManager.setConfiguration(group, key, value);
            return "Set config [" + group + "." + key + "] = " + value;
        }
        catch (Exception e)
        {
            log.error("Failed to set config: {}.{} = {}", group, key, value, e);
            return "Failed to set config: " + e.getMessage();
        }
    }

    /**
     * Get current player stats.
     */
    public String getPlayerStats()
    {
        try
        {
            if (client.getLocalPlayer() == null)
            {
                return "Not logged in - no player stats available.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Player: ").append(client.getLocalPlayer().getName()).append("\n");
            sb.append("Combat Level: ").append(client.getLocalPlayer().getCombatLevel()).append("\n\n");
            sb.append("Skills:\n");
            for (Skill skill : Skill.values())
            {
                if (skill == Skill.OVERALL)
                {
                    continue;
                }
                int level = client.getRealSkillLevel(skill);
                int boosted = client.getBoostedSkillLevel(skill);
                int xp = client.getSkillExperience(skill);
                sb.append(String.format("  %-15s Level: %d/%d  XP: %,d\n",
                    skill.getName(), boosted, level, xp));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            return "Could not retrieve player stats: " + e.getMessage();
        }
    }

    /**
     * Get a summary of RuneLite configuration groups.
     */
    public String listConfigGroups()
    {
        try
        {
            List<String> groups = configManager.getConfigurationKeys("")
                .stream()
                .map(k -> {
                    int dot = k.indexOf('.');
                    return dot > 0 ? k.substring(0, dot) : k;
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());

            if (groups.isEmpty())
            {
                return "No configuration groups found.";
            }
            return "Configuration groups:\n" + groups.stream()
                .map(g -> "- " + g)
                .collect(Collectors.joining("\n"));
        }
        catch (Exception e)
        {
            return "Failed to list config groups: " + e.getMessage();
        }
    }

    /**
     * List all config keys in a given group.
     */
    public String listConfigKeys(String group)
    {
        try
        {
            List<String> keys = configManager.getConfigurationKeys(group + ".")
                .stream()
                .map(k -> k.substring(group.length() + 1))
                .sorted()
                .collect(Collectors.toList());

            if (keys.isEmpty())
            {
                return "No config keys found for group '" + group + "'.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Config keys for [").append(group).append("]:\n");
            for (String key : keys)
            {
                String value = configManager.getConfiguration(group, key);
                sb.append(String.format("  %s = %s\n", key, value));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            return "Failed to list config keys: " + e.getMessage();
        }
    }

    private Plugin findPlugin(String name)
    {
        String lowerName = name.toLowerCase();
        // Try exact match first
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin.getName().equalsIgnoreCase(name))
            {
                return plugin;
            }
        }
        // Try partial match
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin.getName().toLowerCase().contains(lowerName))
            {
                return plugin;
            }
        }
        return null;
    }
}
