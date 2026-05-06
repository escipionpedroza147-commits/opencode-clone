/**
 * OpenCode Java - A terminal-based AI coding agent application.
 * 
 * <p>OpenCode Java is a comprehensive AI-powered coding assistant that runs in the terminal,
 * providing intelligent code generation, file manipulation, system operations, and project
 * management capabilities through natural language interaction.
 * 
 * <h2>Architecture Overview</h2>
 * 
 * <p>The application is organized into several key packages:
 * 
 * <h3>{@link com.opencodejava.core Core Package}</h3>
 * <p>Contains the main application class, configuration management, session handling,
 * memory system, and usage tracking. This is the foundation layer that orchestrates
 * all other components.
 * 
 * <h3>{@link com.opencodejava.agent Agent Package}</h3>
 * <p>Implements the AI agent system with support for multiple specialized agents
 * (build, plan, general) that can execute tools, maintain conversation context,
 * and spawn subagents for complex tasks.
 * 
 * <h3>{@link com.opencodejava.tool Tool Package}</h3>
 * <p>Provides the tool execution framework with implementations for file operations,
 * bash execution, git operations, web search, code search, and image generation.
 * Tools are the primary interface between AI agents and the system.
 * 
 * <h3>{@link com.opencodejava.command Command Package}</h3>
 * <p>Implements user commands for application control, configuration management,
 * session handling, and administrative functions. Commands are prefixed with '/'
 * and provide direct user control over the application.
 * 
 * <h3>{@link com.opencodejava.provider Provider Package}</h3>
 * <p>Abstracts LLM provider implementations (OpenRouter, LM Studio, etc.) with
 * a common interface for agent communication, tool calling, and response streaming.
 * 
 * <h3>{@link com.opencodejava.skill Skill Package}</h3>
 * <p>Manages custom skills and capabilities that can be loaded from markdown files
 * or integrated with external systems. Skills extend agent capabilities beyond
 * the built-in tool set.
 * 
 * <h3>{@link com.opencodejava.ui UI Package}</h3>
 * <p>Provides the terminal user interface with features like markdown rendering,
 * diff display, progress indicators, and interactive input handling.
 * 
 * <h3>{@link com.opencodejava.model Model Package}</h3>
 * <p>Contains data models and configuration classes used throughout the application
 * for representing conversations, messages, agent configurations, and provider settings.
 * 
 * <h2>Key Features</h2>
 * 
 * <ul>
 *   <li><strong>Multi-Agent System:</strong> Specialized agents for different tasks
 *       (build agent with full access, plan agent for read-only analysis, general agent)</li>
 *   <li><strong>Tool Ecosystem:</strong> Rich set of tools for file manipulation, system operations,
 *       version control, web access, and code analysis</li>
 *   <li><strong>Persistent Memory:</strong> AI agents can remember information across sessions
 *       for improved continuity and learning</li>
 *   <li><strong>Session Management:</strong> Save, load, and export conversation sessions
 *       with complete history preservation</li>
 *   <li><strong>Provider Flexibility:</strong> Support for multiple LLM providers with
 *       easy switching and configuration</li>
 *   <li><strong>Safety Features:</strong> Destructive command protection, file size limits,
 *       iteration limits, and error handling</li>
 *   <li><strong>Extensibility:</strong> Custom skills and commands can be added through
 *       configuration and plugin mechanisms</li>
 * </ul>
 * 
 * <h2>Usage Patterns</h2>
 * 
 * <p>The application supports several common usage patterns:
 * 
 * <h3>Interactive Development</h3>
 * <p>Use the build agent to write code, run tests, and iterate on solutions with
 * full access to file system and development tools.
 * 
 * <h3>Code Analysis</h3>
 * <p>Use the plan agent to analyze codebases, understand architecture, and plan
 * changes without modifying files.
 * 
 * <h3>Documentation and Learning</h3>
 * <p>Use the general agent for explanations, documentation generation, and
 * learning about technologies and patterns.
 * 
 * <h3>Project Setup and Configuration</h3>
 * <p>Leverage the tool ecosystem to set up projects, configure build systems,
 * and establish development workflows.
 * 
 * <h2>Configuration</h2>
 * 
 * <p>The application is configured through JSON files (opencode.json) that can be
 * placed in the project directory or user home directory. Configuration includes:
 * 
 * <ul>
 *   <li>LLM provider settings (API keys, endpoints, models)</li>
 *   <li>Agent configurations (prompts, available tools)</li>
 *   <li>Custom commands and skills</li>
 *   <li>Directory paths and preferences</li>
 * </ul>
 * 
 * <h2>Security Considerations</h2>
 * 
 * <p>The application includes several security features:
 * 
 * <ul>
 *   <li><strong>Tool Access Control:</strong> Different agents have different tool access levels</li>
 *   <li><strong>File Size Limits:</strong> Prevent memory exhaustion from large files</li>
 *   <li><strong>Command Confirmation:</strong> Destructive operations require confirmation</li>
 *   <li><strong>Path Validation:</strong> File operations validate paths and permissions</li>
 *   <li><strong>Iteration Limits:</strong> Prevent infinite loops in tool execution</li>
 * </ul>
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
package com.opencodejava;