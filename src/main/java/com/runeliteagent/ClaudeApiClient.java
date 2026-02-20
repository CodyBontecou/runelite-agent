package com.runeliteagent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
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
    private static final Gson GSON = new Gson();

    private final ClaudeAgentConfig config;
    private final OkHttpClient httpClient;

    public ClaudeApiClient(ClaudeAgentConfig config)
    {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Send a message to Claude with tool definitions and conversation history.
     *
     * @param messages  conversation messages array
     * @param tools     tool definitions array
     * @return the parsed JSON response
     */
    public JsonObject sendMessage(JsonArray messages, JsonArray tools) throws IOException
    {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isEmpty())
        {
            throw new IOException("Claude API key not configured. Set it in the plugin settings.");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.modelId());
        requestBody.addProperty("max_tokens", config.maxTokens());
        requestBody.add("messages", messages);
        requestBody.addProperty("system", buildSystemPrompt());

        if (tools != null && tools.size() > 0)
        {
            requestBody.add("tools", tools);
        }

        String jsonBody = GSON.toJson(requestBody);
        log.debug("Claude API request: {}", jsonBody);

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
            log.debug("Claude API response ({}): {}", response.code(), responseBody);

            if (!response.isSuccessful())
            {
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
