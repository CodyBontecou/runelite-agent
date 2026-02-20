# Claude Agent — RuneLite Plugin

An AI-powered assistant plugin for RuneLite that integrates Claude (Anthropic) directly into your OSRS client. Chat with Claude in a sidebar panel to get help with the game, control RuneLite settings, and look up information from the OSRS Wiki.

## Features

### 💬 Chat Interface
- Sidebar chat panel integrated into RuneLite
- Conversation history with clear/reset
- Real-time streaming of responses
- Visual distinction between user messages, assistant responses, and tool usage

### 🔧 RuneLite Control (Agent Tools)
Claude can directly interact with your RuneLite client:
- **List plugins** — See all installed plugins and their enabled/disabled status
- **Enable/disable plugins** — Toggle any plugin on or off by name
- **Read settings** — View current configuration values for any plugin
- **Change settings** — Modify plugin configurations on the fly
- **List config groups/keys** — Browse all available settings

### 📚 OSRS Wiki Integration
- **Search the wiki** — Find articles on any OSRS topic
- **Read wiki pages** — Get full article content for detailed information
- **Item prices** — Look up Grand Exchange pricing

### 📊 Player Stats
- View current skill levels, boosted levels, and XP
- Combat level and player name

## Setup

### 1. Build the plugin

```bash
export JAVA_HOME=/path/to/jdk-11-or-higher
./gradlew build
```

### 2. Install in RuneLite

Copy the built JAR to your RuneLite external plugins directory, or use the RuneLite Developer Tools to load it:

**Option A: External plugin loader**
1. Copy `build/libs/runelite-agent-1.0.0.jar` to `~/.runelite/plugins/`
2. Restart RuneLite

**Option B: Development mode**
1. Clone the RuneLite client source
2. Add this project as a dependency
3. Run from your IDE

### 3. Configure your API key

1. Open RuneLite settings (wrench icon)
2. Find "Claude Agent" in the plugin list
3. Enter your Anthropic API key (starts with `sk-ant-`)
4. Optionally change the model (default: `claude-sonnet-4-20250514`)

### 4. Start chatting

Click the Claude Agent icon in the RuneLite sidebar to open the chat panel.

## Example Prompts

- "What plugins do I have enabled?"
- "Enable the Ground Items plugin"
- "What are the requirements for Dragon Slayer 2?"
- "Show me my stats"
- "What's the best way to train Prayer from 43 to 70?"
- "Change the ground items highlight color to yellow"
- "Disable the idle notifier plugin"
- "How do I get to Zulrah?"
- "What drops does Vorkath have?"

## Architecture

```
ClaudeAgentPlugin          — Main plugin entry point, wires everything together
ClaudeAgentConfig          — Plugin configuration (API key, model, max tokens)
ClaudeAgentPanel           — Swing UI chat panel in RuneLite sidebar
AgentOrchestrator          — Manages conversation loop with Claude tool use
ClaudeApiClient            — HTTP client for Anthropic Messages API
RuneLiteController         — Bridge to RuneLite's ConfigManager & PluginManager
OsrsWikiClient             — OSRS Wiki MediaWiki API client
```

## Requirements

- Java 11+ (for building)
- RuneLite client
- Anthropic API key ([get one here](https://console.anthropic.com/))

## Tech Stack

- **RuneLite Plugin API** — Client integration
- **Anthropic Messages API** — Claude with tool use
- **OkHttp** — HTTP client
- **Gson** — JSON parsing
- **OSRS Wiki API** — MediaWiki API for game data
