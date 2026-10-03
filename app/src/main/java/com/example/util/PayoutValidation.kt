package com.example.util

/**
 * Client-side copy of the checks in the vendor_upsert_bank_details RPC. The messages are the
 * server's own texts, so the vendor sees the same wording whether the app or the server rejects
 * the input. The server stays the source of truth.
 */
object PayoutValidation {
    const val METHOD_BANK = "bank"
    const val METHOD_UPI = "upi"

    private val ACCOUNT_NUMBER_REGEX = Regex("^[0-9]{9,18}$")
    private val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    private val UPI_REGEX = Regex("^[a-z0-9._-]{2,100}@[a-z]{2,64}$")

    fun normalizeAccountNumber(value: String): String = value.filterNot { it.isWhitespace() }
    fun normalizeIfsc(value: String): String = value.trim().uppercase()
    fun normalizeUpi(value: String): String = value.trim().lowercase()

    /** Returns the first validation message, or null when the input would be accepted. */
    fun validate(
        method: String?,
        accountHolderName: String,
        accountNumber: String,
        ifscCode: String,
        upiId: String
    ): String? {
        if (method != METHOD_BANK && method != METHOD_UPI) return "Choose Bank account or UPI"
        if (accountHolderName.isBlank()) return "Account holder name is required"
        return if (method == METHOD_BANK) {
            when {
                !ACCOUNT_NUMBER_REGEX.matches(normalizeAccountNumber(accountNumber)) ->
                    "Enter a valid bank account number (9 to 18 digits)"
                !IFSC_REGEX.matches(normalizeIfsc(ifscCode)) ->
                    "Enter a valid IFSC code (e.g. SBIN0001234)"
                else -> null
            }
        } else {
            if (!UPI_REGEX.matches(normalizeUpi(upiId))) "Enter a valid UPI ID (e.g. name@okaxis)" else null
        }
    }
}
