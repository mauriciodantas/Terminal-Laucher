package app.lawnchair.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    @Test fun commonShapesArePhones() {
        listOf(
            "99999-9999", "9999-9999", "999999999", "(11) 99999-9999", "11 99999-9999",
            "11999999999", "5511999999999", "+55 11 99999-9999", "+1 (415) 555-0100", " 99999 9999 ",
        ).forEach { assertEquals(it, it.trim(), PhoneNumber.of(it)) }
    }

    @Test fun shortNumbersSumsAndTimesAreNot() {
        listOf("42", "1000-250", "12*3+4", "10:30", "1.1.1.1", "abc", "9999-999", "+12", "1234567890123456").forEach {
            assertNull(it, PhoneNumber.of(it))
        }
    }

    @Test fun splitSeparatesTheNumberFromTheMessage() {
        assertEquals("11 99999-9999" to "chego logo", PhoneNumber.split("11 99999-9999 chego logo"))
        assertEquals("99999-9999" to "", PhoneNumber.split("99999-9999"))
        assertEquals("+55 11 99999 9999" to "oi", PhoneNumber.split("+55 11 99999 9999 oi"))
        assertEquals("99999-9999" to "às 10:30", PhoneNumber.split("99999-9999: às 10:30"))
        assertNull(PhoneNumber.split("ana oi"))
    }
}
