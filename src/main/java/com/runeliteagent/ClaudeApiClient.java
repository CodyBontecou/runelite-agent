package com.runeliteagent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class ClaudeApiClient
{
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String CONFIG_GROUP = "claudeagent";
    private static final Gson GSON = new Gson();

    private final ConfigManager configManager;
    private final OkHttpClient httpClient;

    public ClaudeApiClient(ConfigManager configManager)
    {
        this.configManager = configManager;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Read the API key directly from ConfigManager to avoid secret-field persistence issues.
     */
    private String getApiKey()
    {
        String key = configManager.getConfiguration(CONFIG_GROUP, "apiKey");
        return key != null ? key.trim() : "";
    }

    private String getModel()
    {
        String model = configManager.getConfiguration(CONFIG_GROUP, "modelId");
        return model != null && !model.isEmpty() ? model : "claude-sonnet-4-20250514";
    }

    private int getMaxTokens()
    {
        String val = configManager.getConfiguration(CONFIG_GROUP, "maxTokens");
        try
        {
            return val != null ? Integer.parseInt(val) : 4096;
        }
        catch (NumberFormatException e)
        {
            return 4096;
        }
    }

    public JsonObject sendMessage(JsonArray messages, JsonArray tools) throws IOException
    {
        String apiKey = getApiKey();
        if (apiKey.isEmpty())
        {
            throw new IOException("Claude API key not configured. Enter it in the chat panel or plugin settings.");
        }

        log.info("Sending to Claude API. Key length: {}, prefix: {}",
            apiKey.length(),
            apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : "(short)");

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", getModel());
        requestBody.addProperty("max_tokens", getMaxTokens());
        requestBody.add("messages", messages);
        requestBody.addProperty("system", buildSystemPrompt());

        if (tools != null && tools.size() > 0)
        {
            requestBody.add("tools", tools);
        }

        String jsonBody = GSON.toJson(requestBody);

        Request request = new Request.Builder()
            .url(API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(RequestBody.create(jsonBody, JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful())
            {
                log.error("Claude API error ({}): {}", response.code(), responseBody);
                throw new IOException("Claude API error (HTTP " + response.code() + "): " + responseBody);
            }

            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }

    private String buildSystemPrompt()
    {
        return "You are a helpful assistant integrated into RuneLite, the popular Old School RuneScape (OSRS) client. "
            + "You have deep knowledge of OSRS game mechanics, quests, skills, items, monsters, and strategies.\n\n"
            + "You have access to tools that let you:\n"
            + "1. **Control RuneLite** - List, enable, and disable plugins; read and change plugin settings\n"
            + "2. **Search the OSRS Wiki** - Look up any game information from the official Old School RuneScape Wiki\n"
            + "3. **Get player stats** - View the current player's skill levels and XP\n\n"
            + "Guidelines:\n"
            + "- When asked about OSRS topics, use the wiki search tool to provide accurate, up-to-date information\n"
            + "- When asked to change RuneLite settings, use the appropriate config tools\n"
            + "- When toggling plugins, confirm what you've done after the action\n"
            + "- Be concise but thorough. Format responses clearly.\n"
            + "- If a user asks about a quest, boss, item, or mechanic, look it up on the wiki rather than relying on memory\n"
            + "- For plugin config changes, list the current config first so the user can see what's available\n"
            + "- Always explain what changes you're making before making them\n"
            + "- You can chain multiple tool calls in a single response when needed";
    }

    public void shutdown()
    {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
