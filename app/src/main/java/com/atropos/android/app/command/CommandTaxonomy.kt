/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.command

/**
 * Safety and risk classification tier for every Atropos command.
 * Enforces bounded agency and human-in-the-loop governance.
 */
enum class SafetyTier(val label: String, val requiresApproval: Boolean) {
    READ_ONLY("Read Only", false),
    LOCAL_MUTATION("Local Mutation", false),
    NETWORK_EGRESS("Network Egress", true),
    CRITICAL_GOVERNANCE("Critical Governance", true)
}

/**
 * Top-level taxonomy categories preventing information overload when opening `/`.
 */
enum class CommandCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconGlyph: String
) {
    AGENT_EXECUTION(
        "agent",
        "Autonomous Agent & Goals",
        "Launch long-horizon goal trajectories, manage queues, checkpoints, and worker hierarchies",
        "⚡"
    ),
    PROVIDERS_MODELS(
        "providers",
        "Providers & Neural Cascades",
        "Configure Anthropic, OpenAI, Groq, Gemini, DeepSeek, Bedrock, and free-tier cascades",
        "🧠"
    ),
    CODE_AST_HEAL(
        "code",
        "AST Parsing & Self-Healing",
        "Analyze symbol dependency lattices, execute AST self-repair loops, and resolve diagnostics",
        "🔧"
    ),
    WORKSPACE_FILES(
        "workspace",
        "Workspace, Territory & Files",
        "Explore directory trees, inspect file buffers, enforce territory boundaries, and compute diffs",
        "📁"
    ),
    GIT_VCS(
        "git",
        "Git & Version Control",
        "Inspect status, staging, safe commits, branch switches, and gated rebase conflict resolution",
        "🌿"
    ),
    MCP_INTEGRATIONS(
        "mcp",
        "MCP Servers & Tool Governance",
        "Supervise Model Context Protocol hosts, execute bounded tools, and enforce sandbox policy",
        "🔌"
    ),
    DIAGNOSTICS_HUD(
        "diagnostics",
        "Telemetry, Doctor & HUD",
        "Run backend doctor diagnostics, view quota dopamine ledger, and audit execution journals",
        "📊"
    ),
    SPECGRAPH_RESEARCH(
        "specgraph",
        "SpecGraph & Merkle Sealing",
        "Browse project specgraphs, examine research lineages, and verify cryptographic Merkle certificates",
        "📜"
    )
}

/**
 * Structured specification of an argument required or accepted by a command.
 */
data class CommandArgSpec(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val type: String = "String",
    val defaultValue: String? = null
)

/**
 * Structured specification of an optional command flag.
 */
data class CommandFlagSpec(
    val flag: String,
    val alias: String? = null,
    val description: String,
    val takesValue: Boolean = false
)

/**
 * Comprehensive specification of an Atropos command.
 * Powers the interactive `/` category launcher, auto-completion, and deep documentation sheets.
 */
data class AtroposCommandSpec(
    val name: String,
    val category: CommandCategory,
    val subcategory: String? = null,
    val synopsis: String,
    val proseDescription: String,
    val usage: String,
    val arguments: List<CommandArgSpec> = emptyList(),
    val flags: List<CommandFlagSpec> = emptyList(),
    val safetyTier: SafetyTier = SafetyTier.READ_ONLY,
    val exampleUsages: List<String> = emptyList(),
    val requiredCapability: String = "core"
)
