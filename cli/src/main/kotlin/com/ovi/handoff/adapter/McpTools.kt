package com.ovi.handoff.adapter

import com.ovi.handoff.shared.model.PermissionType
import com.ovi.handoff.shared.model.RiskLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The four tools HandOff exposes.
 *
 * Descriptions are written for the model that reads them, not for a human skimming a list. Each one
 * states what the tool blocks on and what a failure means, because an agent that does not know
 * `isError` can mean "denied by the user" will cheerfully retry a command the user just refused.
 */
internal object McpTools {

    const val APPROVE: String = "handoff_approve"
    const val ASK_QUESTION: String = "handoff_ask_question"
    const val REQUEST_PLAN: String = "handoff_request_plan_approval"
    const val STATUS: String = "handoff_status"

    fun all(): List<JsonObject> = listOf(approve(), askQuestion(), requestPlan(), status())

    private fun approve(): JsonObject = buildJsonObject {
        put("name", APPROVE)
        put(
            "description",
            "Ask the user's paired phone to authorize a shell command or file change. Blocks until " +
                "the user decides, the request expires, or HandOff determines no phone can answer. " +
                "Returns isError=true when the action was NOT authorized: read the text, which says " +
                "whether the user denied it or whether the phone could not be reached. Never treat " +
                "an isError result as permission to proceed."
        )
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("command", "The shell command or operation to execute")
                stringProperty("reason", "Why this action is necessary")
                enumProperty(
                    "action_type",
                    "Action category",
                    listOf(
                        PermissionType.SHELL, PermissionType.TERMINAL, PermissionType.FILE_WRITE,
                        PermissionType.FILE_READ, PermissionType.PATCH, PermissionType.NETWORK,
                        PermissionType.MCP, PermissionType.OTHER
                    )
                )
                enumProperty(
                    "risk_level",
                    "How dangerous this action is. Critical requires biometric confirmation on the phone.",
                    listOf(RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL)
                )
                stringProperty("cwd", "Absolute path of the working directory this action applies to")
            }
            putJsonArray("required") { add(JsonPrimitive("command")) }
        }
    }

    private fun askQuestion(): JsonObject = buildJsonObject {
        put("name", ASK_QUESTION)
        put(
            "description",
            "Ask the user a multiple-choice question on their paired phone and wait for the answer. " +
                "Use when a decision is the user's to make and proceeding on a guess would waste work."
        )
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("question", "The question to ask")
                putJsonObject("options") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Selectable answers")
                }
                putJsonObject("is_multi_select") {
                    put("type", "boolean")
                    put("description", "True when more than one option may be chosen")
                }
                stringProperty("cwd", "Absolute path of the working directory this question relates to")
            }
            putJsonArray("required") {
                add(JsonPrimitive("question"))
                add(JsonPrimitive("options"))
            }
        }
    }

    private fun requestPlan(): JsonObject = buildJsonObject {
        put("name", REQUEST_PLAN)
        put(
            "description",
            "Send an implementation plan to the user's paired phone for review before writing code. " +
                "Returns isError=true when the plan was not approved."
        )
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("title", "Title of the plan")
                stringProperty("summary", "Summary of the proposed changes")
                putJsonObject("user_review_required") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Decisions that need explicit consent")
                }
                stringProperty("cwd", "Absolute path of the working directory the plan applies to")
            }
            putJsonArray("required") {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("summary"))
            }
        }
    }

    private fun status(): JsonObject = buildJsonObject {
        put("name", STATUS)
        put(
            "description",
            "Report whether HandOff can actually reach the user's phone: pairing state, relay " +
                "connectivity, detected IDE and workspace. Call this first if an approval failed, " +
                "and relay its guidance to the user verbatim."
        )
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("cwd", "Absolute path of the working directory to report on")
            }
        }
    }

    private fun JsonObjectBuilder.stringProperty(name: String, description: String) {
        putJsonObject(name) {
            put("type", "string")
            put("description", description)
        }
    }

    private fun JsonObjectBuilder.enumProperty(name: String, description: String, values: List<String>) {
        putJsonObject(name) {
            put("type", "string")
            put("description", description)
            putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
        }
    }
}
