package com.tskforging.checktagrs

import org.junit.Assert.*
import org.junit.Test

class TagParserTest {
    @Test
    fun snssKanbanReadsFirstFieldAsPartNumber() {
        val result = TagParser.kanban("7521T0376    260805    80\nCLM012")

        assertTrue(result.success)
        assertEquals("7521T0376", result.partNo)
        assertEquals("KANBAN_SNSS", result.tagType)
    }

    @Test fun parsesConfirmedAisinSample() {
        val raw = "XXXXXXX   3J631   JC21JC 7D42   01213161-17170            J631   0100000753RE02-22"
        val result = TagParser.kanban(raw)
        assertTrue(result.success)
        assertEquals("1213161-17170", result.partNo)
        assertEquals("KANBAN_AISIN", result.tagType)
    }

    @Test fun parsesAisinWithoutJoiningItsPrefixToPreviousField() {
        val result = TagParser.kanban("7D42   0 1213161 - 17170   J631")

        assertTrue(result.success)
        assertEquals("1213161-17170", result.partNo)
        assertEquals("KANBAN_AISIN", result.tagType)
    }

    @Test fun parsesConfirmedDnthSampleAndRequiresMatchingCopies() {
        val raw = "DISC5060020000010101000210125104151120710725124061290515207154081550911 TGY94159-0010 0000012 C07 3163955 T-2 60805369 TGY94159-0010 01"
        val result = TagParser.kanban(raw)
        assertTrue(result.success)
        assertEquals("TGY94159-0010", result.partNo)
        assertEquals("KANBAN_DNTH", result.tagType)
    }

    @Test fun rejectsConflictingDnthCopies() {
        val result = TagParser.kanban("TGY94159-0010 data TGY94159-0011")
        assertFalse(result.success)
    }

    @Test fun parsesDnthTgFormatAndMatchesBoxAfterRemovingIPrefix() {
        val raw = "DISC5060020000010101000210 125104151120710725124061290515207154081550911 TG028351-5130 0000120 C07 3003581 T-3 60805047 TG028351-5130 01"
        val box = TagParser.box("ITG028351-5130")
        val kanban = TagParser.kanban(raw)

        assertTrue(box.success)
        assertTrue(kanban.success)
        assertEquals("TG028351-5130", box.partNo)
        assertEquals("TG028351-5130", kanban.partNo)
        assertEquals(box.partNo, kanban.partNo)
        assertEquals("KANBAN_DNTH", kanban.tagType)
    }

    @Test fun jathBoxVariantMatchesKanbanBasePart() {
        val box = TagParser.box("PD26080501|FP0001|PART|JGF02-002060-31-4")
        val kanban = TagParser.kanban("JGF02-002060-31")

        assertTrue(box.success)
        assertTrue(kanban.success)
        assertEquals("KANBAN_JATH", kanban.tagType)
        assertTrue(TagParser.partsMatch(box.partNo!!, kanban.partNo!!))
        assertEquals("JGF02-002060-31", TagParser.comparisonPart(box.partNo!!))
    }

    @Test fun jathWithoutBoxVariantAlsoMatches() {
        val box = TagParser.box("PD26080501|FP0001|PART|JGF02-002060-31")
        val kanban = TagParser.kanban("JGF02-002060-31")

        assertTrue(box.success)
        assertTrue(kanban.success)
        assertTrue(TagParser.partsMatch(box.partNo!!, kanban.partNo!!))
    }

    @Test fun jathRuleDoesNotStripDnthSuffix() {
        assertEquals("TG028351-5130", TagParser.comparisonPart("TG028351-5130"))
        assertFalse(TagParser.partsMatch("TG028351-5130", "TG028351"))
    }

    @Test fun rejectsUnknownKanbanInsteadOfGuessing() {
        assertFalse(TagParser.kanban("UNKNOWN CUSTOMER DATA").success)
    }

    @Test fun normalizesSpaceInStandAndNewAisinPrefix() {
        assertEquals("16171-05030", TagParser.stand("|16171- 05030").partNo)
        assertEquals("16171-05030", TagParser.kanban("01 16171-05030").partNo)
    }
}
