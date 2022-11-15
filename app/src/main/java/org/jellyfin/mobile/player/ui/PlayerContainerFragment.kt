package org.jellyfin.mobile.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import org.jellyfin.mobile.player.DecoderType

class PlayerContainerFragment : Fragment() {
    private lateinit var fragmentContainer: FragmentContainerView
    private var decoderType: DecoderType = DecoderType.AUTO
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentContainer = FragmentContainerView(requireContext())
        fragmentContainer.id = View.generateViewId()
        setFragment()
        return fragmentContainer
    }

    fun setDecoder(type: DecoderType) {
        decoderType = type
        setFragment(true)
    }

    private fun setFragment(shouldReplace: Boolean = false) {
        childFragmentManager.beginTransaction().apply {
            val args = arguments ?: Bundle()
            args.putString("DECODER", decoderType.name)
            if (shouldReplace) {
                replace(fragmentContainer.id, PlayerFragment::class.java, args)
            } else {
                add(fragmentContainer.id, PlayerFragment::class.java, args)
            }
            commit()
        }
    }
}
