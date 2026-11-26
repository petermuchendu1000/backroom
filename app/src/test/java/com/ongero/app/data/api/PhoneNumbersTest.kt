package com.ongero.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A wrong number here does not fail politely: it sends an M-Pesa PIN prompt to a
 * stranger. Normalising on the device is what makes a typo free.
 */
class PhoneNumbersTest {

    @Test
    fun `accepts the forms people actually type`() {
        val expected = "254712345678"
        listOf(
            "0712345678",
            "0712 345 678",
            "0712-345-678",
            "+254712345678",
            "+254 712 345 678",
            "254712345678",
            "712345678",
            "(0712) 345678",
            "00254712345678",
        ).forEach { input ->
            assertEquals("input: $input", expected, PhoneNumbers.normalise(input))
        }
    }

    @Test
    fun `accepts the 01 range as well as 07`() {
        // Safaricom issues 011x numbers too. Rejecting them would turn away real
        // customers with no way to tell us why.
        assertEquals("254112345678", PhoneNumbers.normalise("0112345678"))
        assertEquals("254112345678", PhoneNumbers.normalise("112345678"))
    }

    @Test
    fun `rejects anything that is not a Kenyan mobile number`() {
        listOf(
            "",
            "   ",
            "0712345",            // too short
            "07123456789",        // too long
            "254812345678",       // 8 is not a mobile prefix
            "0812345678",         // same, in local form
            "+1 555 0100",        // not Kenya
            "254",                // country code alone
            "abcdefghij",         // no digits
            "0000000000",         // right length, not a mobile number
        ).forEach { input ->
            assertNull("input: $input", PhoneNumbers.normalise(input))
        }
    }

    @Test
    fun `isValid agrees with normalise`() {
        assertEquals(true, PhoneNumbers.isValid("0712345678"))
        assertEquals(false, PhoneNumbers.isValid("0812345678"))
    }

    @Test
    fun `the normalised form is what Daraja's own validator accepts`() {
        // backend mpesa.service.ts formatPhoneNumber() rejects anything that does not
        // match this exact pattern, so a number this class blesses must satisfy it or
        // the Daraja gateway will 400 a payment the client thought was fine.
        val darajaPattern = Regex("^254[17]\\d{8}$")
        listOf("0712345678", "0112345678", "+254799999999").forEach { input ->
            val normalised = PhoneNumbers.normalise(input)
            assertEquals("input: $input", true, normalised != null && darajaPattern.matches(normalised))
        }
    }
}
