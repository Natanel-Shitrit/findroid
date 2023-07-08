package dev.jdtech.jellyfin.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.jdtech.jellyfin.player.video.R
import dev.jdtech.jellyfin.viewmodels.PlayerActivityViewModel
import java.lang.IllegalStateException

class QualitySelectionDialogFragment(
    private val viewModel: PlayerActivityViewModel,
) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val qualityTexts = listOf("Original", "4k", "1080p", "720p", "480p", "360p")
        val qualityBitrate = listOf(null, 59616000, 14616000, 7616000, 2616000, 292000)

        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            builder.setTitle(getString(R.string.select_playback_quality))
                .setSingleChoiceItems(
                    qualityTexts.toTypedArray(),
                    qualityBitrate.indexOf(viewModel.playbackBitrate),
                ) { dialog, which ->
                    viewModel.selectBitrate(
                        qualityBitrate[which],
                    )
                    dialog.dismiss()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
