package com.ovi.handoff.core

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/**
 * Filesystem helpers for the files under `~/.handoff` that must not leak: the Ed25519 private key
 * and the config holding the pairing secret.
 *
 * Both were previously written with whatever permissions the platform default gave them, which on a
 * shared machine means any other local account could read the key that signs approval requests.
 */
internal object SecureFiles {

    /**
     * Writes [content] and restricts the file to the current user only.
     *
     * The write goes to a sibling temp file and is then moved into place, so an interrupted write
     * cannot leave a half-written config that the next start would discard and replace, silently
     * rotating the user's pair id.
     */
    fun writeSecureText(target: File, content: String) {
        target.parentFile?.let { parent ->
            parent.mkdirs()
            restrictToOwner(parent, directory = true)
        }

        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        restrictToOwner(temp, directory = false)
        move(temp, target)
        restrictToOwner(target, directory = false)
    }

    fun writeSecureBytes(target: File, content: ByteArray) {
        target.parentFile?.let { parent ->
            parent.mkdirs()
            restrictToOwner(parent, directory = true)
        }

        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(content)
        restrictToOwner(temp, directory = false)
        move(temp, target)
        restrictToOwner(target, directory = false)
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Atomic write with no permission changes, for files we do not own the policy for.
     *
     * Used for third-party IDE configs: they deserve the crash-safety of a temp-and-move, but
     * tightening the permissions on someone else's config directory is not ours to do.
     */
    fun writeAtomicText(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        move(temp, target)
    }

    /**
     * Restricts a path to its owner: POSIX 0600/0700 where available, and on Windows an ACL that
     * names only the current user. Failures are non-fatal, because an unusual filesystem should not
     * stop the daemon from starting, but they are reported once so the user can act on it.
     */
    fun restrictToOwner(file: File, directory: Boolean) {
        val path = file.toPath()

        val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
        if (posix != null) {
            val permissions = if (directory) {
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                )
            } else {
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            }
            runCatching { posix.setPermissions(permissions) }.onFailure { warnOnce(file, it) }
            return
        }

        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
        runCatching {
            val owner = Files.getOwner(path)
            val entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                    AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA,
                    AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.READ_ATTRIBUTES,
                    AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.READ_NAMED_ATTRS,
                    AclEntryPermission.WRITE_NAMED_ATTRS,
                    AclEntryPermission.READ_ACL,
                    AclEntryPermission.SYNCHRONIZE,
                    AclEntryPermission.DELETE
                )
                .build()
            acl.acl = listOf(entry)
        }.onFailure { warnOnce(file, it) }
    }

    private var warned = false

    private fun warnOnce(file: File, cause: Throwable) {
        if (warned) return
        warned = true
        System.err.println(
            "[Handoff] Warning: could not restrict permissions on ${file.absolutePath} (${cause.message}). " +
                "Verify no other local user can read ~/.handoff."
        )
    }
}
