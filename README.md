# OpenCode Java Clone

A Java implementation of the [OpenCode](https://github.com/anomalyco/opencode) AI coding agent.

## Features

- **Multi-Agent System** — Built-in agents (build, plan, general) + user-defined agents with multi-depth subagent support
- **Terminal UI** — JLine-based interactive terminal with agent switching (Tab key), markdown rendering, and streaming responses
- **Dual Provider Support** — OpenRouter (cloud) and LMStudio (local) via OpenAI-compatible API
- **Tool System** — File read/write, bash execution, regex search — all with tool calling
- **Command System** — Built-in commands (/help, /clear, /compact, /quit, /model, /agents) + extensible user-defined commands
- **Skills** — Plugin-based skill system inspired by langchain4j-skills
- **Session Management** — Conversation persistence, history, and context compaction
- **Streaming** — Real-time token streaming via SSE for responsive output

## Requirements

- Java 17+
- Maven 3.8+
- An API key from OpenRouter or a running LMStudio instance

## Quick Start

```bash
# Clone
git clone https://github.com/your-repo/opencode-clone.git
cd opencode-clone

# Set your API key
export OPENROUTER_API_KEY=sk-or-v1-your-key-here

# Build
mvn clean package -DskipTests

# Run
java -jar target/opencode-clone-1.0.0-SNAPSHOT.jar
```

## Configuration

Create an `opencode.json` in your project root or `~/.opencode.json` in your home directory:

```json
{
  "provider": {
    "type": "openrouter",
    "api_key": "sk-or-v1-...",
    "base_url": "https://openrouter.ai/api/v1",
    "model": "anthropic/claude-sonnet-4",
    "temperature": 0.7,
    "max_tokens": 4096
  },
  "agents": {
    "build": {
      "description": "Full-access coding agent",
      "tools": ["file_read", "file_write", "bash", "search"],
      "read_only": false
    },
    "plan": {
      "description": "Read-only planning agent",
      "tools": ["file_read", "search"],
      "read_only": true
    },
    "custom-agent": {
      "description": "My custom agent",
      "system_prompt": "You are a specialist in...",
      "tools": ["file_read", "bash"],
      "read_only": false
    }
  }
}
```

### LMStudio Configuration

```json
{
  "provider": {
    "type": "lmstudio",
    "base_url": "http://localhost:1234/v1",
    "model": "local-model"
  }
}
```

## Architecture

```
com.opencodejava/
├── core/           App entry point, Config, SessionManager
├── provider/       LLM providers (OpenRouter, LMStudio)
├── agent/          Agent system with multi-depth subagents
├── command/        Command system (built-in + extensible)
├── skill/          Skills plugin system
├── tool/           Tools (FileRead, FileWrite, Bash, Search)
├── model/          Data models (Message, Conversation, ToolCall, etc.)
└── ui/             Terminal UI (JLine + flexmark markdown)
```

## Built-in Agents

| Agent | Description | Tools | Mode |
|-------|------------|-------|------|
| **build** | Default coding agent | All | Read/Write |
| **plan** | Analysis & planning | Read + Search | Read-only |
| **general** | Subagent for complex tasks | Read + Bash + Search | Read/Write |

## Commands

| Command | Description |
|---------|-------------|
| `/help` | Show available commands |
| `/clear` | Clear conversation and start fresh |
| `/compact` | Compact conversation history |
| `/quit` | Exit the application |
| `/model [name]` | Show or change the active model |
| `/agents [name]` | List agents or switch to one |

## Keyboard Shortcuts

- **Tab** — Cycle between agents
- **Ctrl+C** — Cancel current operation
- **Ctrl+D** — Quit

## Dependencies

- [LangChain4j](https://github.com/langchain4j/langchain4j) — LLM integration
- [JLine 3](https://github.com/jline/jline3) — Terminal UI
- [Jackson](https://github.com/FasterXML/jackson) — JSON processing
- [flexmark-java](https://github.com/vsch/flexmark-java) — Markdown parsing
- [OkHttp](https://github.com/square/okhttp) — HTTP + SSE streaming

## Subagents

Agents can spawn subagents up to 5 levels deep. The general agent is invoked internally for complex multi-step tasks. Custom subagents can be defined in config:

```json
{
  "agents": {
    "researcher": {
      "description": "Deep research subagent",
      "system_prompt": "You research topics thoroughly...",
      "tools": ["file_read", "bash", "search"]
    }
  }
}
```

## License

MIT
