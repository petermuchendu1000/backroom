package com.ongero.app.data.api

/**
 * Turning what someone types into what M-Pesa needs.
 *
 * Done on the device so a typo costs nothing: the alternative is a round trip that
 * ends in a 400, or worse, a gateway accepting it and sending a PIN prompt to a
 * stranger's phone.
 *
 * The target is 254XXXXXXXXX with no plus. PalPluss accepts 07…, 254… and +254… and
 * normalises internally, but Daraja's own formatter rejects anything that is not
 * ^254[17]\d{8}$, so sending the normalised form keeps both gateways happy and keeps
 * one definition of "valid" instead of two.
 */
object PhoneNumbers {

    /** Safaricom mobile prefixes after the country code: 7xx and 1xx. */
    private val NORMALISED = Regex("^254[17]\\d{8}$")

    /**
     * @return the number as 254XXXXXXXXX, or null if it is not a Kenyan mobile number.
     */
    fun normalise(input: String): String? {
        // Spaces, dashes, brackets and a leading plus are all things people type.
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val candidate = when {
            // 0712345678 / 0112345678
            digits.length == 10 && digits.startsWith("0") -> "254" + digits.drop(1)
            // 712345678 / 112345678
            digits.length == 9 && (digits.startsWith("7") || digits.startsWith("1")) -> "254$digits"
            // 254712345678, with or without the + that filter() already removed
            digits.length == 12 && digits.startsWith("254") -> digits
            // 00254712345678, the international prefix some people still dial
            digits.length == 14 && digits.startsWith("00254") -> digits.drop(2)
            else -> return null
        }

        return if (NORMALISED.matches(candidate)) candidate else null
    }

    fun isValid(input: String): Boolean = normalise(input) != null
}
