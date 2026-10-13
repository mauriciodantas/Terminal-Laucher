package app.lawnchair.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcEvaluatorTest {

    private fun eval(s: String) = CalcEvaluator.evaluate(s)

    @Test fun basicOperations() {
        assertEquals(96.0, eval("12*8")!!, 0.0)
        assertEquals(5.0, eval("2+3")!!, 0.0)
        assertEquals(-1.0, eval("2-3")!!, 0.0)
        assertEquals(2.5, eval("5/2")!!, 0.0)
    }

    @Test fun precedenceAndParentheses() {
        assertEquals(14.0, eval("2+3*4")!!, 0.0)
        assertEquals(20.0, eval("(2+3)*4")!!, 0.0)
        assertEquals(9.0, eval("((1+2))*3")!!, 0.0)
    }

    @Test fun unaryMinusAndPlus() {
        assertEquals(-5.0, eval("-5")!!, 0.0)
        assertEquals(5.0, eval("--5")!!, 0.0)
        assertEquals(1.0, eval("3*-1+4")!!, 0.0)
        assertEquals(5.0, eval("+5")!!, 0.0)
    }

    @Test fun percentDividesByHundred() {
        assertEquals(0.5, eval("50%")!!, 1e-12)
        assertEquals(20.0, eval("200*10%")!!, 1e-12)
    }

    @Test fun commaIsADecimalMark() {
        assertEquals(3.0, eval("1,5*2")!!, 0.0)
        assertEquals(0.25, eval(".25")!!, 0.0)
    }

    @Test fun spacesAreIgnored() {
        assertEquals(96.0, eval(" 12 * 8 ")!!, 0.0)
    }

    @Test fun incompleteOrInvalidIsNull() {
        listOf("", "12*", "(1+2", "1+2)", "abc", "1..2", "*3", "1/0", "()").forEach {
            assertNull(it, eval(it))
        }
    }

    @Test fun formatTrimsZerosAndUsesComma() {
        assertEquals("96", CalcEvaluator.format(96.0))
        assertEquals("2,5", CalcEvaluator.format(2.5))
        assertEquals("0,33333333", CalcEvaluator.format(1.0 / 3))
        assertEquals("0", CalcEvaluator.format(-0.0))
        assertEquals("1000000", CalcEvaluator.format(1e6))
        assertEquals("-3,25", CalcEvaluator.format(-3.25))
    }
}
