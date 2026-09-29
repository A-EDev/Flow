package io.github.aedev.flow.ui.components.donation

import androidx.annotation.StringRes
import io.github.aedev.flow.R

internal const val PATREON_URL = "https://patreon.com/A_EDev"

internal class CryptoWallet(
    @StringRes val name: Int,
    val ticker: String,
    val address: String,
)

/** The wallets the donations page lists; the donation prompt names the same ones. */
internal val DonationWallets =
    listOf(
        CryptoWallet(R.string.donation_currency_bitcoin, "BTC", "bc1qgmkkxxvzvsymtpfazqfl93jw6k4jgy0xmrtnv8"),
        CryptoWallet(R.string.donation_currency_ethereum, "ETH", "0xfbac6f464fec7fe458e318971a42ba45b305b70e"),
        CryptoWallet(R.string.donation_currency_solana, "SOL", "7b3SLgiVPb8qQUvERSPGRWoFoiGEDvkFuY98M1GEngug"),
        CryptoWallet(R.string.donation_currency_usdt, "USDT", "TRz7VDrTWwCLCfQmYBEJakqcZgbFNWfUMP"),
        CryptoWallet(
            R.string.donation_currency_monero,
            "XMR",
            "8AgaxZnpEvT8VXJpczpL7BQejwSEw97saJmKYqq4zKErbe9bkYSwUhJ813msPPbdYhF11oz4N7tfEj4Zi6k27fKD83ca1if",
        ),
    )
