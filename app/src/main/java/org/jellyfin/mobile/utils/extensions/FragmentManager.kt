@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils.extensions

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.add
import androidx.fragment.app.replace
import org.jellyfin.mobile.R

inline fun <reified T : Fragment> FragmentManager.addFragment(args: Bundle? = null) {
    beginTransaction().apply {
        add<T>(R.id.fragment_container, args = args)
        addToBackStack(null)
    }.commit()
}

inline fun <reified T : Fragment> FragmentManager.replaceFragment(args: Bundle? = null) {
    beginTransaction().replace<T>(R.id.fragment_container, args = args).commit()
}
