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
    val type: String? = null
)

class PolicyEngine(private val policyFile: File) {
    
    fun evaluate(command: String?, type: String?): String {
        val policy = loadPolicy()
        
        for (rule in policy.rules) {
            val matchesCommand = rule.condition.commandPattern?.let {
                command?.matches(it.toRegex()) == true
            } ?: true
            
            val matchesType = rule.condition.type?.let {
                type == it
            } ?: true
            
            if (matchesCommand && matchesType) {
                return rule.action
            }
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
