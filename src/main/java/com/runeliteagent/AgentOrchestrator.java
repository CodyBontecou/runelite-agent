package com.runeliteagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AgentOrchestrator
{
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final ClaudeApiClient apiClient;
    private final RuneLiteController controller;
    private final OsrsWikiClient wikiClient;
    private final ClaudeAgentConfig config;
    private final ExecutorService executor;
    private final JsonArray conversationHistory;
    private final JsonArray toolDefinitions;

    public AgentOrchestrator(ClaudeApiClient apiClient, RuneLiteController controller,
                             OsrsWikiClient wikiClient, ClaudeAgentConfig config)
    {
        this.apiClient = apiClient;
        this.controller = controller;
        this.wikiClient = wikiClient;
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "claude-agent-worker");
            t.setDaemon(true);
            return t;
        });
        this.conversationHistory = new JsonArray();
        this.toolDefinitions = buildToolDefinitions();
    }

    /**
     * Send a user message and process the response asynchronously.
     * The onChunk callback receives text segments as they become available.
     * The onComplete callback is called when the full response is ready.
     */
    public void sendMessage(String userMessage, Consumer<String> onChunk, Consumer<String> onComplete, Consumer<String> onError)
    {
        executor.submit(() -> {
            try
            {
                // Add user message to history
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                JsonArray content = new JsonArray();
                JsonObject textBlock = new JsonObject();
                textBlock.addProperty("type", "text");
                textBlock.addProperty("text", userMessage);
                content.add(textBlock);
                userMsg.add("content", content);
                conversationHistory.add(userMsg);

                // Run the agent loop
                StringBuilder fullResponse = new StringBuilder();
                runAgentLoop(fullResponse, onChunk, onError);
                onComplete.accept(fullResponse.toString());
            }
            catch (Exception e)
            {
                log.error("Agent error", e);
                onError.accept("Error: " + e.getMessage());
            }
        });
    }

    private void runAgentLoop(StringBuilder fullResponse, Consumer<String> onChunk, Consumer<String> onError) throws IOException
    {
        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++)
        {
            JsonObject response = apiClient.sendMessage(conversationHistory, toolDefinitions);

            String stopReason = response.has("stop_reason") ? response.get("stop_reason").getAsString() : "";
            JsonArray contentBlocks = response.getAsJsonArray("content");

            // Add assistant response to history
            JsonObject assistantMsg = new JsonObject();
            assistantMsg.addProperty("role", "assistant");
            assistantMsg.add("content", contentBlocks);
            conversationHistory.add(assistantMsg);

            // Process content blocks
            boolean hasToolUse = false;
            JsonArray toolResults = new JsonArray();

            for (JsonElement block : contentBlocks)
            {
                JsonObject blockObj = block.getAsJsonObject();
                String type = blockObj.get("type").getAsString();

                if ("text".equals(type))
                {
                    String text = blockObj.get("text").getAsString();
                    fullResponse.append(text);
                    onChunk.accept(text);
                }
                else if ("tool_use".equals(type))
                {
                    hasToolUse = true;
                    String toolId = blockObj.get("id").getAsString();
                    String toolName = blockObj.get("name").getAsString();
                    JsonObject input = blockObj.getAsJsonObject("input");

                    onChunk.accept("\n🔧 Using tool: " + toolName + "...\n");

                    String result = executeTool(toolName, input);
                    log.info("Tool '{}' result: {}", toolName, result);

                    JsonObject toolResult = new JsonObject();
                    toolResult.addProperty("type", "tool_result");
                    toolResult.addProperty("tool_use_id", toolId);
                    toolResult.addProperty("content", result);
                    toolResults.add(toolResult);
                }
            }

            if (hasToolUse)
            {
                // Add tool results to conversation and continue loop
                JsonObject toolResultMsg = new JsonObject();
                toolResultMsg.addProperty("role", "user");
                toolResultMsg.add("content", toolResults);
                conversationHistory.add(toolResultMsg);
                // Continue the loop for Claude to process tool results
            }
            else
            {
                // No tool use - response is complete
                break;
            }
        }
    }

    private String executeTool(String toolName, JsonObject input)
    {
        try
        {
            switch (toolName)
            {
                case "list_plugins":
                    return controller.listPlugins();

                case "enable_plugin":
                    return controller.enablePlugin(input.get("plugin_name").getAsString());

                case "disable_plugin":
                    return controller.disablePlugin(input.get("plugin_name").getAsString());

                case "get_config":
                    return controller.getConfigValue(
                        input.get("group").getAsString(),
                        input.get("key").getAsString()
                    );

                case "set_config":
                    return controller.setConfigValue(
                        input.get("group").getAsString(),
                        input.get("key").getAsString(),
                        input.get("value").getAsString()
                    );

                case "list_config_groups":
                    return controller.listConfigGroups();

                case "list_config_keys":
                    return controller.listConfigKeys(input.get("group").getAsString());

                case "get_player_stats":
                    return controller.getPlayerStats();

                case "search_wiki":
                    int limit = input.has("limit") ? input.get("limit").getAsInt() : 5;
                    return wikiClient.search(input.get("query").getAsString(), limit);

                case "get_wiki_page":
                    return wikiClient.getPage(input.get("title").getAsString());

                case "get_item_price":
                    return wikiClient.getItemPrice(input.get("item_name").getAsString());

                default:
                    return "Unknown tool: " + toolName;
            }
        }
        catch (Exception e)
        {
            log.error("Tool execution error: {} - {}", toolName, e.getMessage(), e);
            return "Tool error: " + e.getMessage();
        }
    }

    private JsonArray buildToolDefinitions()
    {
        JsonArray tools = new JsonArray();

        // --- RuneLite Control Tools ---
        tools.add(buildTool("list_plugins",
            "List all installed RuneLite plugins and whether they are enabled or disabled.",
            new JsonObject()));

        JsonObject enableInput = new JsonObject();
        addProperty(enableInput, "plugin_name", "string", "Name of the plugin to enable (case-insensitive, partial match supported)");
        addRequired(enableInput, "plugin_name");
        tools.add(buildTool("enable_plugin",
            "Enable a RuneLite plugin by name. The plugin will be started immediately.",
            enableInput));

        JsonObject disableInput = new JsonObject();
        addProperty(disableInput, "plugin_name", "string", "Name of the plugin to disable (case-insensitive, partial match supported)");
        addRequired(disableInput, "plugin_name");
        tools.add(buildTool("disable_plugin",
            "Disable a RuneLite plugin by name. The plugin will be stopped immediately.",
            disableInput));

        tools.add(buildTool("list_config_groups",
            "List all available RuneLite configuration groups. Each group corresponds to a plugin or system setting.",
            new JsonObject()));

        JsonObject listKeysInput = new JsonObject();
        addProperty(listKeysInput, "group", "string", "Configuration group name to list keys for");
        addRequired(listKeysInput, "group");
        tools.add(buildTool("list_config_keys",
            "List all configuration keys and their current values for a specific config group.",
            listKeysInput));

        JsonObject getConfigInput = new JsonObject();
        addProperty(getConfigInput, "group", "string", "Configuration group name (e.g., 'grounditems', 'agility')");
        addProperty(getConfigInput, "key", "string", "Configuration key name");
        addRequired(getConfigInput, "group", "key");
        tools.add(buildTool("get_config",
            "Get the current value of a specific RuneLite plugin configuration setting.",
            getConfigInput));

        JsonObject setConfigInput = new JsonObject();
        addProperty(setConfigInput, "group", "string", "Configuration group name");
        addProperty(setConfigInput, "key", "string", "Configuration key name");
        addProperty(setConfigInput, "value", "string", "New value to set (as a string)");
        addRequired(setConfigInput, "group", "key", "value");
        tools.add(buildTool("set_config",
            "Set a RuneLite plugin configuration value. Use list_config_keys first to see available keys.",
            setConfigInput));

        // --- Player Info Tools ---
        tools.add(buildTool("get_player_stats",
            "Get the current player's skill levels, XP, and combat level. Only works when logged into the game.",
            new JsonObject()));

        // --- OSRS Wiki Tools ---
        JsonObject searchInput = new JsonObject();
        addProperty(searchInput, "query", "string", "Search query for the OSRS Wiki");
        addProperty(searchInput, "limit", "integer", "Maximum number of results (default 5, max 10)");
        addRequired(searchInput, "query");
        tools.add(buildTool("search_wiki",
            "Search the Old School RuneScape Wiki for articles matching a query. Returns titles and snippets.",
            searchInput));

        JsonObject pageInput = new JsonObject();
        addProperty(pageInput, "title", "string", "Exact title of the wiki page to retrieve");
        addRequired(pageInput, "title");
        tools.add(buildTool("get_wiki_page",
            "Get the full text content of a specific OSRS Wiki page by its exact title.",
            pageInput));

        JsonObject priceInput = new JsonObject();
        addProperty(priceInput, "item_name", "string", "Name of the item to look up");
        addRequired(priceInput, "item_name");
        tools.add(buildTool("get_item_price",
            "Look up Grand Exchange price information for an OSRS item.",
            priceInput));

        return tools;
    }

    private JsonObject buildTool(String name, String description, JsonObject properties)
    {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);

        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");

        if (properties.has("properties"))
        {
            inputSchema.add("properties", properties.getAsJsonObject("properties"));
        }
        else
        {
            inputSchema.add("properties", new JsonObject());
        }

        if (properties.has("required"))
        {
            inputSchema.add("required", properties.getAsJsonArray("required"));
        }

        tool.add("input_schema", inputSchema);
        return tool;
    }

    private void addProperty(JsonObject schema, String name, String type, String description)
    {
        if (!schema.has("properties"))
        {
            schema.add("properties", new JsonObject());
        }
        JsonObject prop = new JsonObject();
        prop.addProperty("type", type);
        prop.addProperty("description", description);
        schema.getAsJsonObject("properties").add(name, prop);
    }

    private void addRequired(JsonObject schema, String... names)
    {
        JsonArray required = new JsonArray();
        for (String name : names)
        {
            required.add(name);
        }
        schema.add("required", required);
    }

    /**
     * Clear conversation history to start fresh.
     */
    public void clearHistory()
    {
        while (conversationHistory.size() > 0)
        {
            conversationHistory.remove(0);
        }
    }

    public void shutdown()
    {
        executor.shutdownNow();
        apiClient.shutdown();
    }
}
