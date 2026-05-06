/**
 * AI agent system for intelligent task execution and tool orchestration.
 * 
 * <p>This package implements the core AI agent functionality that enables natural
 * language interaction with development tools and systems. The agent system supports:
 * 
 * <ul>
 *   <li>Multiple specialized agent types (build, plan, general)</li>
 *   <li>Hierarchical agent structures with subagent creation</li>
 *   <li>Tool execution and iterative problem solving</li>
 *   <li>Conversation context management</li>
 *   <li>Error handling and retry mechanisms</li>
 *   <li>Parallel execution capabilities</li>
 * </ul>
 * 
 * <h2>Key Classes:</h2>
 * <ul>
 *   <li>{@link com.opencodejava.agent.Agent} - Core agent implementation with LLM integration</li>
 *   <li>{@link com.opencodejava.agent.AgentManager} - Manages multiple agents and switching</li>
 *   <li>{@link com.opencodejava.agent.ParallelExecutor} - Handles concurrent agent operations</li>
 * </ul>
 * 
 * <h2>Agent Types:</h2>
 * <ul>
 *   <li><strong>Build Agent:</strong> Full access to all tools for active development</li>
 *   <li><strong>Plan Agent:</strong> Read-only access for analysis and planning</li>
 *   <li><strong>General Agent:</strong> Balanced access for general assistance</li>
 * </ul>
 * 
 * <p>Agents maintain conversation history, can spawn subagents for complex tasks,
 * and provide automatic retry logic for handling transient failures.
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
package com.opencodejava.agent;