package org.jellyfin.mobile.sessionbrowser

import kotlin.reflect.KClass

interface LibraryPage<R : LibraryRoute> {
    val route: KClass<R>
    val grid: Boolean

    suspend fun getContent(
        route: R,
        offset: Int,
        limit: Int,
    ): List<LibraryPageElement>
}

inline fun <reified T : LibraryRoute> libraryPage(
    grid: Boolean = false,
    crossinline getContent: suspend (route: T, offset: Int, limit: Int) -> List<LibraryPageElement>,
) = object : LibraryPage<T> {
    override val route = T::class
    override val grid = grid
    override suspend fun getContent(route: T, offset: Int, limit: Int): List<LibraryPageElement> =
        getContent(route, offset, limit)
}
