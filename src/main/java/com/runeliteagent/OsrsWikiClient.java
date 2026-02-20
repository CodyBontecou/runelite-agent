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
    private static final String USER_AGENT = "RuneLite-Claude-Agent/1.0 (https://github.com/runelite)";

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
     * Get item price from the wiki's exchange data.
     */
    public String getItemPrice(String itemName)
    {
        try
        {
            String encodedName = URLEncoder.encode(itemName, StandardCharsets.UTF_8.name());
            // Use the OSRS Wiki real-time prices API
            String url = "https://prices.runescape.wiki/api/v1/osrs/latest";

            // First, find the item ID by searching the wiki
            String searchUrl = WIKI_API + "?action=query&list=search&srsearch=" + encodedName
                + "+Exchange&srnamespace=0&srlimit=3&format=json";

            Request searchReq = new Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();

            try (Response searchResp = httpClient.newCall(searchReq).execute())
            {
                if (!searchResp.isSuccessful() || searchResp.body() == null)
                {
                    return "Price lookup failed.";
                }
                // Return a message directing to wiki for now
                return "For real-time prices, check the Grand Exchange page: " +
                    "https://oldschool.runescape.wiki/w/Module:Exchange/" +
                    itemName.replace(" ", "_") +
                    "\n\nYou can also use the 'search_wiki' tool to find detailed item info.";
            }
        }
        catch (IOException e)
        {
            return "Price lookup failed: " + e.getMessage();
        }
    }

    public void shutdown()
    {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
