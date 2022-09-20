package com.peaceray.codeword.presentation.view.fragments.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.FragmentContainerBinding
import com.peaceray.codeword.presentation.view.fragments.GameInfoFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * A DialogFragment that wraps a GameInfoFragment in a Dialog, passing its OnInteractionListener
 * calls to the containing context.
 */
@AndroidEntryPoint
class GameInfoDialogFragment: CodeWordDialogFragment(R.layout.fragment_container) {

    //region Creation, Arguments, Listener, Controls
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "GameInfoDialogFragment"
        const val ARG_ARGUMENTS = "${NAME}_ARGUMENTS"

        fun newInstance(seed: String?, setup: GameSetup, sections: Array<GameInfoFragment.Sections> = GameInfoFragment.Sections.values()): GameInfoDialogFragment {
            val fragment = GameInfoDialogFragment()

            val args = Bundle()
            args.putBundle(ARG_ARGUMENTS, GameInfoFragment.newArguments(seed, setup, sections))

            fragment.arguments = args
            return fragment
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: FragmentContainerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.commit {
                val fragment = GameInfoFragment()
                fragment.arguments = requireArguments().getBundle(ARG_ARGUMENTS)
                setReorderingAllowed(true)
                replace(R.id.fragmentContainerView, fragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}