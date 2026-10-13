package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * "calc 12*8" or "c 12*8": math, units, currencies and percentages. A sum or a conversion typed
 * alone ("12*8", "5 km em mi") is calc too.
 */
object CalcCommand : BuiltIn(BuiltInCommand.CALC, R.string.cmd_usage_calc, R.string.cmd_help_calc) {

    override fun names(): Set<String> = super.names() + BuiltInCommand.CALC_SHORT

    /** "c" is listed too, as the short form. */
    override fun usages(): List<Pair<String, String>> = super.usages() + (BuiltInCommand.CALC_SHORT to BuiltInCommand.CALC_SHORT + " " + Texts.get(R.string.cmd_usage_calc_short))

    override val requiresArgument = true

    /** After a call and the messaging actions: "2000-1500" is more often a phone than a sum. */
    override val optionOrder = 1

    private val numberStart = Regex("^[-+]?\\(*[\\d.,]")
    private val mathOnly = Regex("^[\\d\\s.,()+\\-*/%]+$")
    private val operator = Regex("[+\\-*/%]")

    /** Only "2000-1500" reads as much like a sum as a phone; "99999-9999" or "(11) ..." do not. */
    private val ambiguousSum = Regex("^\\d{4}-\\d{4}$")

    override fun isApplicable(text: String, ctx: CommandContext): Boolean {
        val t = text.trim()
        return looksLikeCalc(t, ctx.extras.rates) && (PhoneNumber.of(t) == null || ambiguousSum.matches(t))
    }

    /**
     * "12*3+4", "(2+3)/4", "50%" or "5 km em mi": starts with a number and is either made of math
     * signs only, with an operator after the first character (a bare "42" or "-5" is not a sum), or
     * a conversion. A sum still being typed ("12*") counts too, so the preview stays on calc.
     */
    fun looksLikeCalc(text: String, rates: CurrencyRates? = null): Boolean {
        val t = text.trim()
        if (!numberStart.containsMatchIn(t) || t.none { it.isDigit() }) return false
        if (mathOnly.matches(t)) return operator.containsMatchIn(t.drop(1))
        return Conversions.convert(t, rates) != null || (rates == null && Conversions.needsRates(t))
    }

    /** Among the options the result is shown right away: "= 500". */
    override fun option(text: String, ctx: CommandContext): Suggestion {
        val t = text.trim()
        val label = CalcEvaluator.evaluate(t)?.let { "= " + CalcEvaluator.format(it) } ?: word.uppercase()
        return Suggestion("$word $t", label, CommandEngine.optionKind())
    }

    override fun analyze(arg: String, ctx: CommandContext): Analysis = analyze(arg, ctx.extras.rates)

    fun analyze(arg: String, rates: CurrencyRates?): Analysis {
        val expression = arg.trim()
        val value = if (expression.isEmpty()) null else CalcEvaluator.evaluate(expression)
        if (value == null && expression.isNotEmpty()) {
            Conversions.convert(expression, rates)?.let { converted ->
                return Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_expression),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_calc_title),
                    preview = converted.shown,
                    tone = Tone.OK,
                    action = CommandAction.Calc(expression, converted.result),
                )
            }
            if (rates == null && Conversions.needsRates(expression)) {
                return Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_expression),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_calc_title),
                    preview = Conversions.ratesPending(),
                    tone = Tone.WARN,
                    action = null,
                )
            }
        }
        val result = value?.let { CalcEvaluator.format(it) }
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_expression),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_calc_title),
            preview = when {
                result != null -> "$expression = $result"
                expression.isEmpty() -> Texts.get(R.string.cmd_calc_empty)
                else -> Texts.get(R.string.cmd_calc_incomplete)
            },
            tone = if (result != null) Tone.OK else Tone.IDLE,
            action = result?.let { CommandAction.Calc(expression, it) },
        )
    }
}
