package com.tskforging.checktagrs

enum class ScanTarget { STAND, BOX_TAG, KANBAN }

data class ParseResult(val success: Boolean, val partNo: String?, val tagType: String, val ruleId: String, val ruleVersion: String, val message: String = "")

data class ScanEvidence(
    val eventId: String, val sessionId: String, val sequence: Int, val scannedAt: Long,
    val target: ScanTarget, val raw: String, val sha256: String, val tagType: String,
    val partNo: String?, val ruleId: String, val ruleVersion: String, val parseResult: String,
    val compareResult: String, val rescanOf: String? = null
)

data class HistoryItem(
    val sessionId: String,
    val startedAt: Long,
    val result: String,
    val employeeName: String,
    val partNo: String,
    val standMode: String
)
