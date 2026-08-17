package com.tskforging.checktagrs

data class EmployeeInfo(val name: String, val raw: String)

object EmployeeParser {
    fun parse(scanned: String): EmployeeInfo? {
        val raw = scanned.trim()
        if (!raw.startsWith("EMPLOYEE|", ignoreCase = true)) return null
        val name = raw.substringAfter('|').trim()
        if (name.isEmpty()) return null
        return EmployeeInfo(name, raw)
    }
}
