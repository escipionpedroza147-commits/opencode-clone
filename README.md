# OpenCode Java

A terminal-based AI coding agent written in Java — inspired by [OpenCode](https://github.com/opencode-ai/opencode). Talk to LLMs, run tools, edit files, and build software right from your terminal.

## Features

- 🤖 **Multi-agent system** — Switch between `build`, `plan`, and `general` agents with Tab
- 🛠️ **Built-in tools** — File read/write/edit, bash execution, git, web search/fetch, code search, image generation
- 🔄 **Streaming responses** — Real-time token streaming with progress indicators
- 📝 **Memory system** — Persistent memory across sessions
- ⚡ **Multiple providers** — OpenRouter, LM Studio, or any OpenAI-compatible API
- 🎯 **Glob file search** — Find files with patterns like `*.java`, `**/*.xml`
- 🔒 **Destructive command protection** — Confirmation required for `rm`, `rmdir`, etc.
- 📊 **Token usage tracking** — See how many tokens you're using per session
- 💾 **Session management** — Save, list, and switch sessions
- 📤 **Export conversations** — Export to markdown for sharing

## Installation

### Prerequisites

- Java 17+ (JDK)
- Maven 3.8+
- An API key from [OpenRouter](https://openrouter.ai/) or a local LM Studio instance

### Build from Source

```bash
git clone https://github.com/escipionpedroza147-commits/opencode-clone.git
cd opencode-clone
mvn clean package -DskipTests
```

### Run

```bash
# Set your API key
export OPENROUTER_API_KEY="sk-or-v1-your-key-here"

# Run the agent
java -jar target/opencode-clone-1.0.0-SNAPSHOT.jar
```

## Configuration

Create `opencode.json` in your project root or `~/.opencode.json` for global config:

```json
{
  "provider": {
    "type": "openrouter",
    "api_key": "${OPENROUTER_API_KEY}",
    "base_url": "https://openrouter.ai/api/v1",
    "model": "anthropic/claude-sonnet-4",
    "temperature": 0.7,
    "max_tokens": 16384
  },
  "agents": {
    "build": {
      "description": "Full-access coding agent",
      "tools": ["file_read", "file_write", "file_edit", "bash", "search", "list_files", "git"]
    }
  },
  "commands": {
    "test": {
      "description": "Run tests",
      "script": "mvn test",
      "timeout": "120"
    }
  }
}
```

Environment variable interpolation is supported using `${VAR_NAME}` syntax.

## Commands

| Command | Description |
|---------|-------------|
| `/help` | Show available commands |
| `/model [name]` | Switch model |
| `/agents` | List available agents |
| `/config` | Show current configuration |
| `/session [new\|list]` | Manage sessions |
| `/export [filename]` | Export conversation to markdown |
| `/search <query>` | Search conversation history |
| `/undo` | Remove last message pair |
| `/remember <note>` | Save a note to memory |
| `/memory` | Show stored memories |
| `/usage` | Show token usage stats |
| `/compact` | Compact conversation history |
| `/clear` | Clear terminal |
| `/quit` | Exit |

## Tools

| Tool | Description |
|------|-------------|
| `file_read` | Read file contents |
| `file_write` | Write/create files |
| `file_edit` | Make targeted edits to files |
| `bash` | Execute shell commands (with destructive command protection) |
| `search` | Search codebase with ripgrep-style matching |
| `list_files` | List directory tree with glob support |
| `git` | Git operations |
| `web_search` | Search the web |
| `web_fetch` | Fetch web page content |
| `image_gen` | Generate images |

## Architecture

```
src/main/java/com/opencodejava/
├── core/         # App, Config, Memory, SessionManager
├── provider/     # LLM providers (OpenRouter, LM Studio)
├── agent/        # Agent system and parallel execution
├── model/        # Data models (Message, Conversation, ToolCall)
├── command/      # Slash commands
├── tool/         # Built-in tools
├── skill/        # Skill system
└── ui/           # Terminal UI (JLine-based)
```

## Error Handling

- **Rate limiting**: Automatic exponential backoff with retry on 429 responses
- **Connection errors**: Configurable retry logic (up to 3 attempts)
- **Malformed responses**: Graceful degradation instead of crashes
- **Destructive commands**: Requires explicit confirmation for `rm`, `rmdir`, etc.

## Development

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package
mvn package

# Run with debug logging
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar target/opencode-clone-1.0.0-SNAPSHOT.jar
```

## License

MIT
