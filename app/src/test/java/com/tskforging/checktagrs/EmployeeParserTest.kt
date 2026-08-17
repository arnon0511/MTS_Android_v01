package com.tskforging.checktagrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmployeeParserTest {
    @Test fun readsAllTestEmployees() {
        listOf("Mr.Burin", "Mr.Sakarin", "Mr.Wirachai").forEach { name ->
            assertEquals(name, EmployeeParser.parse("EMPLOYEE|$name")?.name)
        }
    }

    @Test fun rejectsTagAndBlankEmployee() {
        assertNull(EmployeeParser.parse("JGF02-002060-31"))
        assertNull(EmployeeParser.parse("EMPLOYEE|  "))
    }
}
