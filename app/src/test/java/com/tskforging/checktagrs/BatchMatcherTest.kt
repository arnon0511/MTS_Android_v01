package com.tskforging.checktagrs

import org.junit.Assert.*
import org.junit.Test

class BatchMatcherTest {
    @Test fun matchesOutOfOrderByPartNumber() {
        val boxes = listOf(BatchBox(1, "AAA-01"), BatchBox(2, "BBB-02"))
        assertEquals(1, BatchMatcher.findFirstUnmatched(boxes, "BBB-02"))
    }

    @Test fun duplicatePartsUseFirstUnmatchedBox() {
        val boxes = listOf(BatchBox(1, "AAA-01", "AAA-01"), BatchBox(2, "AAA-01"))
        assertEquals(1, BatchMatcher.findFirstUnmatched(boxes, "AAA-01"))
    }

    @Test fun unknownPartDoesNotConsumeBox() {
        val boxes = listOf(BatchBox(1, "AAA-01"))
        assertEquals(-1, BatchMatcher.findFirstUnmatched(boxes, "CCC-03"))
        assertFalse(BatchMatcher.isComplete(boxes))
    }
}
