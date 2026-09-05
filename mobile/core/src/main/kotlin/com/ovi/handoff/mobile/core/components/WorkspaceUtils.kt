package com.ovi.handoff.mobile.core.components

/**
 * Shortens a workspace path or URI (e.g. "c:\Users\foo\my-project") to its display basename ("my-project").
 */
fun shortWorkspaceName(pathOrName: String?): String? {
    if (pathOrName.isNullOrBlank()) return null
    var s = pathOrName.trim()
    if (s.startsWith("file://", ignoreCase = true)) {
        s = s.removePrefix("file:///").removePrefix("file://")
    }
    s = s.trimEnd('/', '\\')
    val basename = s.substringAfterLast('/').substringAfterLast('\\')
    return basename.ifBlank { s }
}
