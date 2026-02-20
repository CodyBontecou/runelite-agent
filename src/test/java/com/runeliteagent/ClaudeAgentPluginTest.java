package com.runeliteagent;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClaudeAgentPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ClaudeAgentPlugin.class);
        RuneLite.main(args);
    }
}
