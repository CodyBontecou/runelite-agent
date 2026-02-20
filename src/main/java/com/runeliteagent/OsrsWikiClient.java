package com.runeliteagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class OsrsWikiClient
{
    private static final String WIKI_API = "https://oldschool.runescape.wiki/api.php";
    private static final String USER_AGENT = "RuneLite-Claude-Agent/1.0 (https://github.com/CodyBontecou/runelite-agent)";

    private final OkHttpClient httpClient;

    public OsrsWikiClient()
    {
        this.httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();
    }

    /**
     * Search the OSRS Wiki and return article summaries.
     */
    public String search(String query, int limit)
    {
        try
        {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = WIKI_API + "?action=query&list=search&srsearch=" + encodedQuery
                + "&srlimit=" + limit + "&srprop=snippet|titlesnippet&format=json";

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return "Wiki search failed: HTTP " + response.code();
                }
                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonObject queryObj = json.getAsJsonObject("query");
                JsonArray results = queryObj.getAsJsonArray("search");

                if (results.size() == 0)
                {
                    return "No wiki results found for: " + query;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("OSRS Wiki search results for '").append(query).append("':\n\n");
                for (JsonElement elem : results)
                {
                    JsonObject result = elem.getAsJsonObject();
                    String title = result.get("title").getAsString();
                    String snippet = result.get("snippet").getAsString()
                        .replaceAll("<[^>]+>", "") // strip HTML tags
                        .replaceAll("&[a-z]+;", " "); // strip HTML entities
                    sb.append("## ").append(title).append("\n");
                    sb.append(snippet).append("\n");
                    sb.append("URL: https://oldschool.runescape.wiki/w/")
                        .append(title.replace(" ", "_")).append("\n\n");
                }
                return sb.toString();
            }
        }
        catch (IOException e)
        {
            log.error("Wiki search failed", e);
            return "Wiki search failed: " + e.getMessage();
        }
    }

    /**
     * Get the full text content of a specific wiki page.
     */
    public String getPage(String title)
    {
        try
        {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name());
            String url = WIKI_API + "?action=query&titles=" + encodedTitle
                + "&prop=extracts&exintro=false&explaintext=true&format=json";

            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    return "Wiki page fetch failed: HTTP " + response.code();
                }
                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonObject pages = json.getAsJsonObject("query").getAsJsonObject("pages");

                for (String key : pages.keySet())
                {
                    if ("-1".equals(key))
                    {
                        return "Wiki page not found: " + title;
                    }
                    JsonObject page = pages.getAsJsonObject(key);
                    String pageTitle = page.get("title").getAsString();
                    String extract = page.has("extract") ? page.get("extract").getAsString() : "No content available.";

                    // Truncate very long pages
                    if (extract.length() > 8000)
                    {
                        extract = extract.substring(0, 8000) + "\n\n[... content truncated for length ...]";
                    }

                    return "# " + pageTitle + "\n\n" + extract;
                }
                return "No page data returned.";
            }
        }
        catch (IOException e)
        {
            log.error("Wiki page fetch failed", e);
            return "Wiki page fetch failed: " + e.getMessage();
        }
    }

    /**
     * Get item price from the OSRS Wiki real-time prices API.
     * First resolves the item ID via the wiki mapping endpoint, then fetches the latest price.
     */
    public String getItemPrice(String itemName)
    {
        try
        {
            // Step 1: Get item mapping to find the item ID
            String mappingUrl = "https://prices.runescape.wiki/api/v1/osrs/mapping";
            Request mappingReq = new Request.Builder()
                .url(mappingUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();

            int itemId = -1;
            String resolvedName = itemName;

            try (Response mappingResp = httpClient.newCall(mappingReq).execute())
            {
                if (!mappingResp.isSuccessful() || mappingResp.body() == null)
                {
                    return "Price lookup failed: could not fetch item mapping.";
                }
                String body = mappingResp.body().string();
                JsonArray items = JsonParser.parseString(body).getAsJsonArray();
                String lowerName = itemName.toLowerCase();

                // Try exact match first, then partial
                for (JsonElement elem : items)
                {
                    JsonObject item = elem.getAsJsonObject();
                    String name = item.get("name").getAsString();
                    if (name.equalsIgnoreCase(itemName))
                    {
                        itemId = item.get("id").getAsInt();
                        resolvedName = name;
                        break;
                    }
                }
                if (itemId == -1)
                {
                    for (JsonElement elem : items)
                    {
                        JsonObject item = elem.getAsJsonObject();
                        String name = item.get("name").getAsString();
                        if (name.toLowerCase().contains(lowerName))
                        {
                            itemId = item.get("id").getAsInt();
                            resolvedName = name;
                            break;
                        }
                    }
                }
            }

            if (itemId == -1)
            {
                return "Item not found: " + itemName + ". Try a more specific name.";
            }

            // Step 2: Get latest price for this item
            String priceUrl = "https://prices.runescape.wiki/api/v1/osrs/latest?id=" + itemId;
            Request priceReq = new Request.Builder()
                .url(priceUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();

            try (Response priceResp = httpClient.newCall(priceReq).execute())
            {
                if (!priceResp.isSuccessful() || priceResp.body() == null)
                {
                    return "Price lookup failed: could not fetch price data.";
                }
                String body = priceResp.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                JsonObject data = json.getAsJsonObject("data");
                JsonObject itemData = data.getAsJsonObject(String.valueOf(itemId));

                if (itemData == null)
                {
                    return "No price data available for " + resolvedName + " (ID: " + itemId + ").";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Grand Exchange Price for **").append(resolvedName).append("** (ID: ").append(itemId).append(")\n\n");

                if (itemData.has("high") && !itemData.get("high").isJsonNull())
                {
                    sb.append("Instant buy: ").append(String.format("%,d", itemData.get("high").getAsLong())).append(" gp\n");
                }
                if (itemData.has("low") && !itemData.get("low").isJsonNull())
                {
                    sb.append("Instant sell: ").append(String.format("%,d", itemData.get("low").getAsLong())).append(" gp\n");
                }
                if (itemData.has("highTime") && !itemData.get("highTime").isJsonNull())
                {
                    long ts = itemData.get("highTime").getAsLong();
                    long minutesAgo = (System.currentTimeMillis() / 1000 - ts) / 60;
                    sb.append("Last buy: ").append(minutesAgo).append(" min ago\n");
                }
                if (itemData.has("lowTime") && !itemData.get("lowTime").isJsonNull())
                {
                    long ts = itemData.get("lowTime").getAsLong();
                    long minutesAgo = (System.currentTimeMillis() / 1000 - ts) / 60;
                    sb.append("Last sell: ").append(minutesAgo).append(" min ago\n");
                }

                sb.append("\nWiki: https://oldschool.runescape.wiki/w/")
                    .append(resolvedName.replace(" ", "_"));

                return sb.toString();
            }
        }
        catch (IOException e)
        {
            log.error("Price lookup failed", e);
            return "Price lookup failed: " + e.getMessage();
        }
    }

    public void shutdown()
    {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
