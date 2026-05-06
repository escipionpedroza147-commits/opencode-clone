# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2026-05-05

### Added
- **Error Handling & Resilience**
  - Exponential backoff with jitter for rate-limited (429) requests
  - Automatic retry logic (up to 3 attempts) for connection errors and 5xx responses
  - Retry-After header support
  - Graceful handling of malformed JSON responses from providers
  - Error response format detection in API responses

- **Configuration Improvements**
  - Environment variable interpolation in config files (`${VAR_NAME}` syntax)
  - `/config` command to display current configuration
  - `/session` command to list and manage sessions

- **Conversation Features**
  - `/export` command to export conversation to markdown
  - `/undo` command to remove last user/assistant message pair
  - `/search <query>` command to search conversation history

- **Tool Improvements**
  - File glob support in ListFilesTool (e.g. `*.java`, `**/*.xml`)
  - Destructive command protection in BashTool (requires `confirmed=true` for rm, rmdir, etc.)
  - Elapsed time reporting for long-running bash commands

- **UI Improvements**
  - ToolSpinner with elapsed time display during tool execution
  - Better progress indication for streaming responses

- **Testing**
  - JUnit 5 and Mockito test dependencies
  - 15+ unit tests covering Config, Memory, ToolRegistry, Message, Conversation, BashTool, ListFilesTool, and OpenRouterProvider

- **Documentation**
  - Comprehensive README with installation, configuration, and command reference
  - This CHANGELOG

### Changed
- OpenRouterProvider now implements retry logic with exponential backoff
- BashTool now blocks destructive commands without explicit confirmation
- ListFilesTool now supports glob patterns via the `glob` parameter
- Config loading now interpolates environment variables before parsing JSON

## [1.0.0] - 2026-04-01

### Added
- Initial release
- Multi-agent system (build, plan, general agents)
- OpenRouter and LM Studio provider support
- Built-in tools: file_read, file_write, file_edit, bash, search, list_files, git, web_search, web_fetch, image_gen
- Terminal UI with JLine
- Memory system
- Session persistence
- Token usage tracking
- Tab completion for commands, agents, and file paths
