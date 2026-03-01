package com.steuerauszug.backend.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.xml.parsers.DocumentBuilderFactory

@Service
class EstvExchangeRateService(private val restTemplate: RestTemplate) {

    companion object {
        private val log = LoggerFactory.getLogger(EstvExchangeRateService::class.java)
        private const val BAZG_URL = "https://www.backend-rates.bazg.admin.ch/api/xmlavgmonth"
        private val DENOMINATION_REGEX = Regex("""(\d+)\s+\w+""")
    }

    fun getAnnualAverageRate(currency: String, year: Int): BigDecimal {
        val rates = (1..12).mapNotNull { month ->
            try { fetchMonthlyRate(currency, year, month) }
            catch (e: Exception) {
                log.warn("Failed to fetch rate for {}/{}-{}: {}", currency, year, month, e.message)
                null
            }
        }
        if (rates.isEmpty()) return BigDecimal.ONE
        val sum = rates.fold(BigDecimal.ZERO) { acc, r -> acc + r }
        return sum.divide(BigDecimal(rates.size), 10, RoundingMode.HALF_UP)
    }

    fun getYearEndRate(currency: String, year: Int): BigDecimal =
        try { fetchMonthlyRate(currency, year, 12) }
        catch (e: Exception) {
            log.warn("Failed to fetch year-end rate for {}/{}: {}", currency, year, e.message)
            BigDecimal.ONE
        }

    private fun fetchMonthlyRate(currency: String, year: Int, month: Int): BigDecimal {
        val url = "$BAZG_URL?year=$year&month=$month"
        val xml = restTemplate.getForObject(url, String::class.java) ?: return BigDecimal.ONE
        return parseRate(xml, currency)
    }

    private fun parseRate(xml: String, currency: String): BigDecimal {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val devises = doc.getElementsByTagName("devise")
        val targetCode = currency.lowercase()

        for (i in 0 until devises.length) {
            val devise = devises.item(i) as? Element ?: continue
            if (devise.getAttribute("code") != targetCode) continue

            val waehrung = devise.getElementsByTagName("waehrung").item(0)?.textContent ?: continue
            val kursText = devise.getElementsByTagName("kurs").item(0)?.textContent ?: continue
            val rate = kursText.replace(",", ".").toBigDecimalOrNull() ?: continue
            val denomination = extractDenomination(waehrung) ?: BigDecimal.ONE

            return rate.divide(denomination, 10, RoundingMode.HALF_UP)
        }

        log.warn("Currency '{}' not found in BAZG response", currency)
        return BigDecimal.ONE
    }

    private fun extractDenomination(waehrung: String): BigDecimal? {
        val match = DENOMINATION_REGEX.find(waehrung.trim()) ?: return BigDecimal.ONE
        return match.groupValues[1].toBigDecimalOrNull()
    }
}
