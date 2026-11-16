package com.quickbill.app.ui

import com.quickbill.app.data.Bill
import com.quickbill.app.data.FavouriteItem

internal fun billNumberQuery(input: String): String = buildString {
    for (character in input) {
        when (character) {
            in '0'..'9' -> append(character)
            in '०'..'९' -> append('0' + (character - '०'))
        }
    }
}

internal fun filterHistory(history: List<Bill>, query: String): List<Bill> {
    val digits = billNumberQuery(query)
    if (digits.isEmpty()) return history
    return history.filter { it.billNumber.toString().contains(digits) }
}

internal fun filterFavourites(favourites: List<FavouriteItem>, query: String): List<FavouriteItem> {
    val name = query.trim()
    if (name.isEmpty()) return favourites
    return favourites.filter { it.name.contains(name, ignoreCase = true) }
}

internal fun calculateChange(totalPaisa: Long, cashReceivedPaisa: Long?): Long? {
    if (totalPaisa < 0 || cashReceivedPaisa == null || cashReceivedPaisa < totalPaisa) return null
    return cashReceivedPaisa - totalPaisa
}
