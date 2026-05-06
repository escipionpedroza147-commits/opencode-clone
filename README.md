# OpenCode Java Clone

A Java implementation of the [OpenCode](https://github.com/anomalyco/opencode) AI coding agent, built with LangChain4j, JLine, Jackson, and flexmark-java.

## Features

### Subagents — Built-in & User-Defined
- **Built-in agents**: `build` (full access), `plan` (read-only analysis), `general` (multi-step research)
- **User-defined agents**: Add custom agents in `opencode.json` with their own system prompts, tool access, and behavior
- **Multi-depth subagents**: Agents can spawn subagents up to 5 levels deep for complex task decomposition
- **@agent delegation**: Type `@general research this codebase` to delegate tasks inline

### Commands — Built-in & User-Defined
- **Built-in commands**: `/help`, `/clear`, `/compact`, `/quit`, `/model`, `/agents`
- **User-defined commands**: Define custom commands in `opencode.json` that execute scripts
- Commands are extensible — add project-specific shortcuts like `/test`, `/build`, `/deploy`

### Skills — LangChain4j Integration
- **langchain4j-skills**: Full integration via `LangChain4jSkillAdapter` — any `@Tool` annotated class works
- **langchain4j-experimental-skills-shell**: Built-in shell skill with multi-command execution, directory control, and timeout management
- **User-defined skills**: Script-based or prompt-based skills loaded from config
- Skills inject capabilities into agent system prompts automatically

### Providers — OpenRouter & LMStudio
- **OpenRouter**: Cloud-based access to Claude, GPT-4, Llama, Gemma, and 200+ models
- **LMStudio**: Local model support via OpenAI-compatible API
- Streaming (SSE) support for real-time token output
- Proper function/tool calling with automatic retry

## Requirements

- Java 17+
- Maven 3.8+
- An API key from OpenRouter OR a running LMStudio instance

## Quick Start

```bash
# Set your API key
export OPENROUTER_API_KEY=sk-or-v1-your-key-here

# Clone and build
git clone https://github.com/escipionpedroza147-commits/opencode-clone.git
cd opencode-clone
mvn clean package -DskipTests

# Run from your project directory
cd /path/to/your/project
java -jar /path/to/opencode-clone/target/opencode-clone-1.0.0-SNAPSHOT.jar
```

## Configuration

Create `opencode.json` in your project root or `~/.opencode.json`:

```json
{
  "provider": {
    "type": "openrouter",
    "api_key": "sk-or-v1-...",
    "base_url": "https://openrouter.ai/api/v1",
    "model": "anthropic/claude-sonnet-4",
    "temperature": 0.7,
    "max_tokens": 16384
  },
  "agents": {
    "build": {
      "description": "Full-access coding agent",
      "tools": ["file_read", "file_write", "file_edit", "bash", "search", "list_files", "git"],
      "read_only": false
    },
    "plan": {
      "description": "Read-only planning agent",
      "tools": ["file_read", "search", "list_files"],
      "read_only": true
    },
    "general": {
      "description": "Research subagent",
      "tools": ["file_read", "bash", "search", "list_files", "git"],
      "read_only": false
    },
    "my-custom-agent": {
      "description": "My specialized agent",
      "system_prompt": "You are an expert in React and TypeScript...",
      "tools": ["file_read", "file_write", "bash", "search"],
      "read_only": false
    }
  },
  "commands": {
    "test": {
      "description": "Run project tests",
      "script": "mvn test",
      "timeout": 300
    },
    "deploy": {
      "description": "Deploy to production",
      "script": "./deploy.sh",
      "timeout": 120
    }
  },
  "skills": {
    "code-review": {
      "description": "Automated code review",
      "type": "prompt",
      "system_prompt": "When reviewing code, check for security, performance, and best practices."
    },
    "docker": {
      "description": "Docker management skill",
      "type": "script",
      "script": "/usr/local/bin/docker-helper.sh",
      "timeout": 60
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
├── core/           App, Config, SessionManager
├── provider/       LLMProvider interface, OpenRouter, LMStudio
├── agent/          Agent (multi-depth), AgentManager (@agent delegation)
├── command/        Built-in commands + UserCommand (user-defined)
├── skill/          SkillRegistry, LangChain4jSkillAdapter, LangChain4jShellSkill, UserSkill
├── tool/           FileRead, FileWrite, FileEdit, Bash, Search, ListFiles, Git
├── model/          Message, Conversation, ToolCall, AgentConfig, ProviderConfig
└── ui/             TerminalUI (JLine), MarkdownRenderer (flexmark)
```

## Built-in Agents

| Agent | Description | Tools | Mode |
|-------|------------|-------|------|
| **build** | Default coding agent | All 7 tools | Read/Write |
| **plan** | Analysis & planning | Read + Search + ListFiles | Read-only |
| **general** | Subagent for complex tasks | Read + Bash + Search + ListFiles + Git | Read/Write |

## Tools (7 Built-in)

| Tool | Description | Read-only |
|------|-------------|-----------|
| `file_read` | Read files with line ranges | ✓ |
| `file_write` | Create/overwrite files | ✗ |
| `file_edit` | Surgical find-and-replace edits | ✗ |
| `bash` | Execute shell commands | ✗ |
| `search` | Regex search across codebase | ✓ |
| `list_files` | Project tree explorer | ✓ |
| `git` | Git operations (status, diff, commit, etc.) | ✗ |

## Commands

| Command | Description |
|---------|-------------|
| `/help` | Show available commands |
| `/clear` | Clear conversation and start fresh |
| `/compact` | Compact conversation history |
| `/quit` | Exit the application |
| `/model [name]` | Show or change the active model |
| `/agents [name]` | List agents or switch to one |
| User-defined | Any command from `opencode.json` |

## Subagent Delegation

Delegate tasks to specific agents using `@agent` syntax:

```
@general Search this codebase for all API endpoints and summarize them
@plan Review the architecture of the authentication module
@my-custom-agent Refactor the React components in src/components/
```

Subagents can spawn their own subagents up to 5 levels deep, enabling complex task decomposition.

## Skills System

### LangChain4j Integration

Any class with `@Tool` annotations from LangChain4j works:

```java
// Automatically discovered and available to agents
public class MyCustomSkill {
    @Tool("Search the web for information")
    public String webSearch(String query) { ... }

    @Tool("Analyze an image")
    public String analyzeImage(String path) { ... }
}
```

### Built-in Skills

- **langchain4j-shell**: Enhanced shell execution (multi-command, directory control, timeouts)
- **shell**: Basic shell execution skill

### User-Defined Skills

Add to `opencode.json`:
- **Script-based**: Execute external scripts with parameters
- **Prompt-based**: Inject specialized knowledge into agents

## Keyboard Shortcuts

- **Tab** — Cycle between agents
- **Ctrl+C** — Cancel current operation
- **Ctrl+D** — Quit

## Dependencies

- [LangChain4j](https://github.com/langchain4j/langchain4j) — LLM integration & @Tool framework
- [JLine 3](https://github.com/jline/jline3) — Terminal UI
- [Jackson](https://github.com/FasterXML/jackson) — JSON processing
- [flexmark-java](https://github.com/vsch/flexmark-java) — Markdown parsing & rendering
- [OkHttp](https://github.com/square/okhttp) — HTTP + SSE streaming

## License

MIT
