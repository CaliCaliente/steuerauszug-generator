package com.steuerauszug.backend.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal

class EstvExchangeRateServiceTest {

    private val restTemplate = mockk<RestTemplate>()
    private val service = EstvExchangeRateService(restTemplate)

    private fun usdXml(kurs: String, waehrung: String = "1 USD") = """
        <?xml version="1.0" encoding="UTF-8"?>
        <wechselkurse>
            <devise code="usd">
                <waehrung>$waehrung</waehrung>
                <kurs>$kurs</kurs>
            </devise>
        </wechselkurse>
    """.trimIndent()

    @Test
    fun `should parse rate for known currency`() {
        every { restTemplate.getForObject(any<String>(), String::class.java) } returns usdXml("0.9050")

        val rate = service.getYearEndRate("USD", 2024)

        assertEquals(0, BigDecimal("0.9050").compareTo(rate))
    }

    @Test
    fun `should divide rate by denomination when denomination is 100`() {
        val egpXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <wechselkurse>
                <devise code="egp">
                    <waehrung>100 EGP</waehrung>
                    <kurs>1.85</kurs>
                </devise>
            </wechselkurse>
        """.trimIndent()
        every { restTemplate.getForObject(any<String>(), String::class.java) } returns egpXml

        val rate = service.getYearEndRate("EGP", 2024)

        // 1.85 / 100 = 0.0185
        assertEquals(0, BigDecimal("0.0185").compareTo(rate.setScale(4)))
    }

    @Test
    fun `should return BigDecimal ONE when currency not found`() {
        val xmlWithoutUsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <wechselkurse>
                <devise code="eur">
                    <waehrung>1 EUR</waehrung>
                    <kurs>0.9600</kurs>
                </devise>
            </wechselkurse>
        """.trimIndent()
        every { restTemplate.getForObject(any<String>(), String::class.java) } returns xmlWithoutUsd

        val rate = service.getYearEndRate("USD", 2024)

        assertEquals(BigDecimal.ONE, rate)
    }

    @Test
    fun `should return BigDecimal ONE when restTemplate returns null`() {
        every { restTemplate.getForObject(any<String>(), String::class.java) } returns null

        val rate = service.getYearEndRate("USD", 2024)

        assertEquals(BigDecimal.ONE, rate)
    }

    @Test
    fun `getAnnualAverageRate should average 12 monthly rates`() {
        every { restTemplate.getForObject(any<String>(), String::class.java) } returns usdXml("0.9000")

        val rate = service.getAnnualAverageRate("USD", 2024)

        // All 12 months return 0.9000, average is 0.9000
        assertEquals(0, BigDecimal("0.9000").compareTo(rate.setScale(4)))
    }

    @Test
    fun `getAnnualAverageRate should return ONE when all months fail`() {
        every { restTemplate.getForObject(any<String>(), String::class.java) } throws RuntimeException("Network error")

        val rate = service.getAnnualAverageRate("USD", 2024)

        assertEquals(BigDecimal.ONE, rate)
    }
}
