package com.ovi.handoff.core

import kotlinx.serialization.Serializable
import com.charleskorn.kaml.Yaml
import java.io.File

@Serializable
data class Policy(
    val defaultAction: String = "ask",
    val rules: List<PolicyRule> = emptyList()
)

@Serializable
data class PolicyRule(
    val action: String, // "allow", "deny", "ask"
    val condition: PolicyCondition
)

@Serializable
data class PolicyCondition(
    val commandPattern: String? = null,
    val type: String? = null,
    val exactCommand: String? = null,
    val forbiddenTokens: List<String>? = null
)

object CommandTokenizer {
    fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val currentToken = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escapeNext = false

        for (char in command) {
            if (escapeNext) {
                currentToken.append(char)
                escapeNext = false
                continue
            }
            when (char) {
                '\\' -> if (!inSingleQuote) escapeNext = true else currentToken.append(char)
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote else currentToken.append(char)
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote else currentToken.append(char)
                ' ', '\t', '\n', ';', '|', '&' -> {
                    if (!inSingleQuote && !inDoubleQuote) {
                        if (currentToken.isNotEmpty()) {
                            tokens.add(currentToken.toString())
                            currentToken.clear()
                        }
                        // Also treat operators as standalone tokens to detect piped exfiltration
                        if (char in listOf(';', '|', '&')) {
                            tokens.add(char.toString())
                        }
                    } else {
                        currentToken.append(char)
                    }
                }
                else -> currentToken.append(char)
            }
        }
        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken.toString())
        }
        return tokens
    }
}

class PolicyEngine(private val policyFile: File) {
    
    fun evaluate(command: String?, type: String?): String {
        val policy = loadPolicy()
        val tokens = command?.let { CommandTokenizer.tokenize(it) } ?: emptyList()
        
        for (rule in policy.rules) {
            var isMatch = true
            
            rule.condition.type?.let { 
                if (type != it) isMatch = false 
            }
            
            rule.condition.commandPattern?.let { pattern ->
                if (command?.matches(pattern.toRegex()) != true) isMatch = false
            }

            rule.condition.exactCommand?.let { exact ->
                // Check if the primary executable matches
                if (tokens.firstOrNull() != exact && !tokens.contains("; $exact") && !tokens.contains("| $exact")) {
                    isMatch = false
                }
            }

            rule.condition.forbiddenTokens?.let { forbidden ->
                if (tokens.none { it in forbidden }) isMatch = false
            }
            
            if (isMatch) return rule.action
        }
        
        return policy.defaultAction
    }

    private fun loadPolicy(): Policy {
        if (!policyFile.exists()) {
            return Policy()
        }
        val content = policyFile.readText()
        return Yaml.default.decodeFromString(Policy.serializer(), content)
    }
}
