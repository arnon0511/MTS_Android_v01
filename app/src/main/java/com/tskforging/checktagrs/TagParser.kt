package com.tskforging.checktagrs

object TagParser {
    private fun clean(raw: String) = raw.removeSuffix("\r\n").removeSuffix("\n").removeSuffix("\r")

    /** Removes print/scanner whitespace without changing meaningful Part No. characters. */
    fun normalizePart(value: String): String = value
        .replace(Regex("\\s+"), "")
        .uppercase()

    /**
     * Returns the value used only for comparison. JATH box/stand labels may
     * append a one-digit variant to a Jxx##-######-## Part No., while the
     * Kanban prints the base Part No. without that variant.
     */
    fun comparisonPart(value: String): String {
        val normalized = normalizePart(value)
        val jath = Regex("^([A-Z]{3}\\d{2}-\\d{6}-\\d{2})(?:-\\d)?$")
            .matchEntire(normalized)
        return jath?.groupValues?.get(1) ?: normalized
    }

    fun partsMatch(expected: String, actual: String): Boolean =
        comparisonPart(expected) == comparisonPart(actual)

    fun stand(rawInput: String): ParseResult {
        val raw = clean(rawInput)
        val fields = raw.split('|')
        return if (fields.size == 2 && fields[0].isEmpty() && fields[1].isNotEmpty())
            ParseResult(true, normalizePart(fields[1]), "STAND", "stand_pipe_field_2_normalized", "2.0")
        else ParseResult(false, null, "UNKNOWN", "stand_pipe_field_2", "1.0", "Stand ต้องมีรูปแบบ |PART-NO")
    }

    fun box(rawInput: String): ParseResult {
        val raw = clean(rawInput)
        val fields = raw.split('|')
        if (fields.size == 2 && fields[0].isEmpty() && fields[1].isNotEmpty())
            return ParseResult(true, normalizePart(fields[1]), "PLASTIC_BOX", "plastic_pipe_field_2_normalized", "2.0")
        if (raw.startsWith("PD") && fields.size >= 4 && fields[3].isNotEmpty())
            return ParseResult(true, normalizePart(fields[3]), "FG_TAG", "fg_pipe_field_4_normalized", "2.0")
        // DNTH box labels may add an "I" prefix to the Kanban Part No.
        // Example: ITG028351-5130 on BOX TAG matches TG028351-5130 on KANBAN.
        val dnthBox = Regex("^I(TG\\d{6}-\\d{4}|TGY\\d{5}-\\d{4})$", RegexOption.IGNORE_CASE)
            .matchEntire(normalizePart(raw))
        if (dnthBox != null)
            return ParseResult(true, dnthBox.groupValues[1].uppercase(), "DNTH_BOX", "dnth_i_prefix_removed", "2.1")
        return ParseResult(false, null, "UNKNOWN", "box_auto", "1.0", "ไม่รู้จักรูปแบบ Box Tag")
    }

    fun kanban(rawInput: String): ParseResult {
        val raw = clean(rawInput)
        if (raw.isBlank())
            return ParseResult(false, null, "UNKNOWN", "kanban_customer_auto", "1.0", "Kanban ว่าง")

        // AISIN confirmed sample contains 0 + seven digits + hyphen + five digits.
        // The leading zero is a Kanban prefix and is not part of the Part No.
        val upperRaw = raw.uppercase()
        // New Aisin format: prefix 01 + a five-digit/five-digit Part No.
        val aisinShortMatches = Regex(
            "(?<!\\d)01\\s*(\\d{5}\\s*-\\s*\\d{5})(?!\\d)"
        ).findAll(upperRaw)
            .map { normalizePart(it.groupValues[1]) }
            .distinct()
            .toList()
        if (aisinShortMatches.size == 1)
            return ParseResult(true, aisinShortMatches.first(), "KANBAN_AISIN", "aisin_01_short_part_normalized", "2.0")
        if (aisinShortMatches.size > 1)
            return ParseResult(false, null, "KANBAN_AISIN", "aisin_01_short_part_normalized", "2.0", "พบ Part No. Aisin มากกว่า 1 ค่าที่ไม่ตรงกัน")

        val aisinMatches = Regex(
            "(?<!\\d)0\\s*(\\d{7}\\s*-\\s*\\d{5})(?!\\d)"
        ).findAll(upperRaw)
            .map { normalizePart(it.groupValues[1]) }
            .distinct()
            .toList()
        if (aisinMatches.size == 1)
            return ParseResult(true, aisinMatches.first(), "KANBAN_AISIN", "aisin_leading_zero_part_normalized", "2.0")
        if (aisinMatches.size > 1)
            return ParseResult(false, null, "KANBAN_AISIN", "aisin_leading_zero_part", "1.0", "พบ Part No. Aisin มากกว่า 1 ค่าที่ไม่ตรงกัน")

        // DNTH prints the same Part No. twice. Both copies must agree.
        // Supported families: legacy TGY#####-#### and TG######-####.
        // Keep separators while locating DNTH values. Searching `compact` would
        // join the preceding/following fields to TGY and break the boundaries.
        val dnthMatches = Regex("(?<![A-Z0-9])(?:TGY\\d{5}|TG\\d{6})-\\d{4}(?![A-Z0-9])")
            .findAll(raw.uppercase()).map { normalizePart(it.value) }.toList()
        if (dnthMatches.size >= 2 && dnthMatches.distinct().size == 1)
            return ParseResult(true, dnthMatches.first(), "KANBAN_DNTH", "dnth_repeated_part", "2.1")
        if (dnthMatches.isNotEmpty())
            return ParseResult(false, null, "KANBAN_DNTH", "dnth_repeated_part", "2.1", "Part No. DNTH ต้องพบซ้ำอย่างน้อย 2 ตำแหน่งและต้องตรงกัน")

        // JATH Kanban uses the base Part No. such as JGF02-002060-31.
        // Some related box/stand labels append a one-digit variant (for
        // example -4); comparisonPart() removes it only for this family.
        val jathMatches = Regex("(?<![A-Z0-9])([A-Z]{3}\\d{2}-\\d{6}-\\d{2})(?:-\\d)?(?![A-Z0-9-])")
            .findAll(raw.uppercase())
            .map { comparisonPart(it.value) }
            .distinct()
            .toList()
        if (jathMatches.size == 1)
            return ParseResult(true, jathMatches.first(), "KANBAN_JATH", "jath_base_part_optional_variant", "1.0")
        if (jathMatches.size > 1)
            return ParseResult(false, null, "KANBAN_JATH", "jath_base_part_optional_variant", "1.0", "พบ Part No. JATH มากกว่า 1 ค่าที่ไม่ตรงกัน")

        // SNSS QR sample: 7521T0376  260805  80\nCLM012
        // Field 1 is Part No.; field 2 is YYMMDD, field 3 is quantity,
        // and the final field is the CLM reference.
        val snss = Regex(
            "^([A-Z0-9][A-Z0-9-]{3,})\\s+(\\d{6})\\s+(\\d+)\\s+(CLM[A-Z0-9-]+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(raw.trim().replace(Regex("\\s+"), " "))
        if (snss != null)
            return ParseResult(true, normalizePart(snss.groupValues[1]), "KANBAN_SNSS", "snss_part_date_qty_clm", "1.0")

        return ParseResult(false, null, "UNKNOWN", "kanban_customer_auto", "1.0", "ยังไม่มีกติกาสำหรับ Kanban รูปแบบนี้")
    }

    fun firstDifference(expected: String, actual: String): String {
        val expectedForCompare = comparisonPart(expected)
        val actualForCompare = comparisonPart(actual)
        val common = minOf(expectedForCompare.length, actualForCompare.length)
        val i = (0 until common).firstOrNull { expectedForCompare[it] != actualForCompare[it] } ?: common
        if (i == expectedForCompare.length && i == actualForCompare.length) return "ตรงกันทุกตัวอักษร"
        val e = expectedForCompare.getOrNull(i)?.toString() ?: "<ไม่มี>"
        val a = actualForCompare.getOrNull(i)?.toString() ?: "<ไม่มี>"
        return "ต่างกันที่ตำแหน่ง ${i + 1}: ควรเป็น [$e] แต่อ่านได้ [$a]"
    }
}
