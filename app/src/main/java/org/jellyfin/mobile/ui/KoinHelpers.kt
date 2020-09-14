package org.jellyfin.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.core.Koin
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.mp.KoinPlatformTools

@Composable
inline fun <reified T> inject(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): Lazy<T> = remember {
    val context = KoinPlatformTools.defaultContext().get()
    context.inject(qualifier, parameters = parameters)
}

@Composable
inline fun <reified T> get(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
): T = remember {
    val context = KoinPlatformTools.defaultContext().get()
    context.get(qualifier, parameters)
}

@Composable
fun getKoin(): Koin = remember {
    val context = KoinPlatformTools.defaultContext().get()
    context.get()
}
