package com.spendtracker

object SmsParser {
    data class Debit(val amount: Double, val label: String)

    private val debitWord = Regex("""\b(debited|spent|withdrawn|purchase|paid|sent)\b""", RegexOption.IGNORE_CASE)
    private val creditWord = Regex("""\b(credited|received|deposited|refund(ed)?|reversed|reversal|cashback)\b""", RegexOption.IGNORE_CASE)
    private val blocked = Regex("""\b(otp|will be debited|declined|failed|unsuccessful|mandate|request(ed)?)\b""", RegexOption.IGNORE_CASE)
    private val amt1 = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val amt2 = Regex("""(?:debited|spent|withdrawn)\s*(?:by|for|of|with)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val who = Regex("""\b(?:to|at|towards)\s+([A-Za-z0-9@._& -]{3,40})""", RegexOption.IGNORE_CASE)
    private val cut = Regex("""\s+(on|ref|upi|avl|bal|via|txn)\b.*""", RegexOption.IGNORE_CASE)

    /** Returns a Debit ONLY for debit messages. Anything else (credits, OTPs, chats) returns null and is discarded unstored. */
    fun parse(sender: String, body: String): Debit? {
        if (!sender.any { it.isLetter() }) return null          // ignore messages from personal phone numbers
        if (blocked.containsMatchIn(body)) return null
        val d = debitWord.find(body) ?: return null
        val c = creditWord.find(body)
        if (c != null && c.range.first < d.range.first) return null  // credit wording first => credit SMS
        val a = (amt1.find(body) ?: amt2.find(body))?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (a <= 0) return null
        val label = who.find(body)?.groupValues?.get(1)?.replace(cut, "")?.trim().takeUnless { it.isNullOrBlank() } ?: "Debit"
        return Debit(a, label)
    }
}
