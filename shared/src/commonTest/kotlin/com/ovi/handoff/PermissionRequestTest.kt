package com.ovi.handoff

import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PermissionRequestTest {

    private fun createRequest(
        project: String? = null,
        workspace: String? = null,
        cwd: String? = null
    ): PermissionRequest {
        return PermissionRequest(
            id = "req-1",
            protocolVersion = "1.0",
            agent = AgentInfo(id = "antigravity", name = "Antigravity"),
            session = SessionInfo(id = "sess-1", project = project, workspace = workspace),
            permission = PermissionInfo(type = "bash", target = null, cwd = cwd, command = "ls"),
            risk = RiskInfo(level = "low", reasons = emptyList()),
            options = listOf("once", "session", "deny"),
            createdAt = "2026-09-03T12:00:00Z",
            expiresAt = "2026-09-03T12:05:00Z"
        )
    }

    @Test
    fun resolvesFilesystemPathWhenAvailable() {
        val req = createRequest(project = "my-awesome-app", workspace = "repo/workspace", cwd = "/home/user/code")
        assertEquals("/home/user/code", req.resolveProjectOrWorkspace())
    }

    @Test
    fun fallsBackToWorkspacePath() {
        val reqPosix = createRequest(workspace = "/projects/handoff-mobile/")
        assertEquals("/projects/handoff-mobile", reqPosix.resolveProjectOrWorkspace())

        val reqWindows = createRequest(workspace = """C:\Users\Developer\Workspaces\HandoffApp\""")
        assertEquals("""C:\Users\Developer\Workspaces\HandoffApp""", reqWindows.resolveProjectOrWorkspace())
    }

    @Test
    fun fallsBackToCwdPathWhenSessionEmpty() {
        val req = createRequest(cwd = """c:\Users\USERAS\Desktop\HandOff\handoff""")
        assertEquals("""c:\Users\USERAS\Desktop\HandOff\handoff""", req.resolveProjectOrWorkspace())
    }

    @Test
    fun filtersOutGenericIdeNames() {
        val req = createRequest(project = "Antigravity IDE", workspace = """C:\Users\USERAS\Desktop\HandOff\handoff""")
        assertEquals("""C:\Users\USERAS\Desktop\HandOff\handoff""", req.resolveProjectOrWorkspace())
    }

    @Test
    fun returnsNullWhenAllAreBlank() {
        val req = createRequest(project = "", workspace = "  ", cwd = null)
        assertNull(req.resolveProjectOrWorkspace())
    }
}
