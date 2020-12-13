@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.add
import androidx.fragment.app.replace
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R

inline fun <reified T : Fragment> FragmentManager.addFragment() {
    beginTransaction().apply {
        add<T>(R.id.fragment_container)
        addToBackStack(null)
    }.commit()
}

inline fun <reified T : Fragment> FragmentManager.replaceFragment(args: Bundle? = null) {
    beginTransaction().replace<T>(R.id.fragment_container, args = args).commit()
}

inline fun Fragment.requireMainActivity(): MainActivity = requireActivity() as MainActivity
