package com.tskforging.checktagrs

data class BatchBox(val scanNo: Int, val partNo: String, var kanbanPart: String? = null)

object BatchMatcher {
    fun findFirstUnmatched(boxes: List<BatchBox>, kanbanPart: String): Int =
        boxes.indexOfFirst { it.kanbanPart == null && TagParser.partsMatch(it.partNo, kanbanPart) }

    fun isComplete(boxes: List<BatchBox>): Boolean = boxes.isNotEmpty() && boxes.all { it.kanbanPart != null }
}
