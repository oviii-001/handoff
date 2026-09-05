package com.ovi.handoff.core

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.isApproval

/**
 * What came back from asking the phone.
 *
 * This used to be a nullable [PermissionDecision], which collapsed every unhappy path into `null`.
 * The agent was then told "no decision arrived before the request expired" whether the user had
 * never paired a phone, the phone was switched off, the relay had refused the request, or the user
 * genuinely ignored it — four situations with four different fixes. Worse, the first three of those
 * were reported only *after* blocking for the full five-minute deadline.
 *
 * Naming them separately is what lets the tool call return in seconds with an instruction the user
 * can act on.
 */
public sealed interface ApprovalOutcome {

    /** The paired phone answered. [decision] may still be a denial. */
    public data class Decided(val decision: PermissionDecision) : ApprovalOutcome

    /** No phone has ever completed pairing with this desktop, so nothing could answer. */
    public data object NotPaired : ApprovalOutcome

    /** The relay confirmed no phone was attached and no push could be sent. */
    public data object PhoneUnreachable : ApprovalOutcome

    /** The relay declined to accept the request, e.g. too many are already queued for this pair. */
    public data class RejectedByRelay(val reason: String) : ApprovalOutcome

    /** The request was delivered but nobody decided before its deadline. */
    public data object Expired : ApprovalOutcome

    /** The desktop could not reach the relay at all. */
    public data class RelayUnreachable(val reason: String) : ApprovalOutcome
}

/** True only when a real decision came back and it authorized the action. */
public fun ApprovalOutcome.isApproved(): Boolean =
    this is ApprovalOutcome.Decided && decision.isApproval()

/** The decision if there was one, so existing call sites can keep reading it directly. */
public fun ApprovalOutcome.decisionOrNull(): PermissionDecision? =
    (this as? ApprovalOutcome.Decided)?.decision

/**
 * A short explanation aimed at whoever is waiting, in plain language and naming the next step.
 *
 * Deliberately written for two audiences at once: it is shown in a terminal by `--exec` and handed
 * to an LLM as an MCP tool result, so it has to state both what happened and what would fix it.
 */
public fun ApprovalOutcome.explain(): String = when (this) {
    is ApprovalOutcome.Decided -> {
        buildString {
            append("Decision: ${decision.decision}")
            decision.selectedOptions?.takeIf { it.isNotEmpty() }?.let {
                append(" | Selected: ${it.joinToString(", ")}")
            }
            decision.feedback?.takeIf { it.isNotBlank() }?.let { append(" | Feedback: $it") }
        }
    }

    ApprovalOutcome.NotPaired ->
        "No phone is paired with this desktop, so nothing was authorized. " +
            "Run `handoff --pair` in a terminal and scan the code with the HandOff app, then retry."

    ApprovalOutcome.PhoneUnreachable ->
        "Your phone is not reachable: it has no connection to the relay and could not be woken by a " +
            "notification. Nothing was authorized. Open the HandOff app on your phone and retry."

    is ApprovalOutcome.RejectedByRelay ->
        "The relay refused the request ($reason), so nothing was authorized. " +
            "Answer or dismiss the approvals already waiting on your phone, then retry."

    ApprovalOutcome.Expired ->
        "The request reached your phone but nobody decided before it expired. Nothing was authorized."

    is ApprovalOutcome.RelayUnreachable ->
        "Could not reach the HandOff relay ($reason), so nothing was authorized. " +
            "Check your network, then run `handoff --doctor` to diagnose."
}
