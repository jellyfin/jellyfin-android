package org.jellyfin.mobile.sessionbrowser.page

import org.jellyfin.mobile.sessionbrowser.LibraryItemAction
import org.jellyfin.mobile.sessionbrowser.LibraryPageElement
import org.jellyfin.mobile.sessionbrowser.LibraryRoute

const val ALPHA_BROWSER_OTHER = "#"
const val ALPHA_BROWSER_LETTERS = "abcdefghijklmnopqrstuvwxyz"
fun createAlphaBrowser(
    offset: Int,
    limit: Int,
    createRoute: (letter: String) -> LibraryRoute,
) = "$ALPHA_BROWSER_OTHER$ALPHA_BROWSER_LETTERS".map { letter ->
    val route = createRoute(letter.toString())

    LibraryPageElement.Item(
        title = letter.uppercase(),
        action = LibraryItemAction.Navigate(route),
    )
}.drop(offset).take(limit)
