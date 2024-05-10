/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.call.conference.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.R
import org.linphone.core.Participant
import org.linphone.core.tools.Log
import org.linphone.databinding.CallConferenceParticipantsListFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.call.adapter.ConferenceParticipantsListAdapter
import org.linphone.ui.call.fragment.GenericCallFragment
import org.linphone.ui.call.viewmodel.CurrentCallViewModel
import org.linphone.ui.main.history.model.ConfirmationDialogModel
import org.linphone.utils.DialogUtils

class ConferenceParticipantsListFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Conference Participants List Fragment]"
    }

    private lateinit var binding: CallConferenceParticipantsListFragmentBinding

    private lateinit var viewModel: CurrentCallViewModel

    private lateinit var adapter: ConferenceParticipantsListAdapter

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (findNavController().currentDestination?.id == R.id.conferenceAddParticipantsFragment) {
            // Holds fragment in place while new fragment slides over it
            return AnimationUtils.loadAnimation(activity, R.anim.hold)
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ConferenceParticipantsListAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallConferenceParticipantsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = requireActivity().run {
            ViewModelProvider(this)[CurrentCallViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        observeToastEvents(viewModel)
        observeToastEvents(viewModel.conferenceModel)

        binding.participantsList.setHasFixedSize(true)
        binding.participantsList.layoutManager = LinearLayoutManager(requireContext())

        if (binding.participantsList.adapter != adapter) {
            binding.participantsList.adapter = adapter
        }

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setAddParticipantsClickListener {
            if (findNavController().currentDestination?.id == R.id.conferenceParticipantsListFragment) {
                val action =
                    ConferenceParticipantsListFragmentDirections.actionConferenceParticipantsListFragmentToConferenceAddParticipantsFragment()
                findNavController().navigate(action)
            }
        }

        viewModel.conferenceModel.participants.observe(viewLifecycleOwner) {
            Log.i("$TAG participants list updated with [${it.size}] items")
            adapter.submitList(it)
        }

        viewModel.conferenceModel.removeParticipantEvent.observe(viewLifecycleOwner) {
            it.consume { pair ->
                val displayName = pair.first
                val participant = pair.second
                showKickParticipantDialog(displayName, participant)
            }
        }
    }

    private fun showKickParticipantDialog(displayName: String, participant: Participant) {
        val model = ConfirmationDialogModel()
        val dialog = DialogUtils.getKickConferenceParticipantConfirmationDialog(
            requireActivity(),
            model,
            displayName
        )

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                viewModel.conferenceModel.kickParticipant(participant)
                val message = getString(R.string.conference_participant_was_kicked_out_toast)
                val icon = R.drawable.check
                (requireActivity() as GenericActivity).showGreenToast(message, icon)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
