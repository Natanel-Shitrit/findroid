package dev.jdtech.jellyfin.fragments

import android.R as AndroidR
import android.app.DownloadManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.bindCardItemImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.databinding.EpisodeBottomSheetBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.dialogs.getStorageSelectionDialog
import dev.jdtech.jellyfin.dialogs.getVideoVersionDialog
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.utils.setTintColor
import dev.jdtech.jellyfin.utils.setTintColorAttribute
import dev.jdtech.jellyfin.viewmodels.EpisodeBottomSheetViewModel
import dev.jdtech.jellyfin.viewmodels.PlayerViewModel
import java.text.DateFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.DateTime
import timber.log.Timber

@AndroidEntryPoint
class EpisodeBottomSheetFragment : BottomSheetDialogFragment() {
    private val args: EpisodeBottomSheetFragmentArgs by navArgs()

    private lateinit var binding: EpisodeBottomSheetBinding
    private val viewModel: EpisodeBottomSheetViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = EpisodeBottomSheetBinding.inflate(inflater, container, false)

        binding.itemActions.playButton.setOnClickListener {
            binding.itemActions.playButton.setImageResource(AndroidR.color.transparent)
            binding.itemActions.progressCircular.isVisible = true
            playerViewModel.loadPlayerItems(viewModel.item)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    Timber.d("$uiState")
                    when (uiState) {
                        is EpisodeBottomSheetViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                        is EpisodeBottomSheetViewModel.UiState.Loading -> bindUiStateLoading()
                        is EpisodeBottomSheetViewModel.UiState.Error -> bindUiStateError(uiState)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadStatus.collect { (status, progress) ->
                    when (status) {
                        DownloadManager.STATUS_PENDING -> {
                            binding.itemActions.downloadButton.isEnabled = false
                            binding.itemActions.downloadButton.setImageResource(AndroidR.color.transparent)
                            binding.itemActions.progressDownload.isIndeterminate = true
                            binding.itemActions.progressDownload.isVisible = true
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            binding.itemActions.downloadButton.isEnabled = false
                            binding.itemActions.downloadButton.setImageResource(AndroidR.color.transparent)
                            binding.itemActions.progressDownload.isVisible = true
                            if (progress < 5) {
                                binding.itemActions.progressDownload.isIndeterminate = true
                            } else {
                                binding.itemActions.progressDownload.isIndeterminate = false
                                binding.itemActions.progressDownload.setProgressCompat(progress, true)
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_trash)
                            binding.itemActions.progressDownload.isVisible = false
                            binding.itemActions.downloadButton.isEnabled = true
                        }
                        else -> {
                            binding.itemActions.progressDownload.isVisible = false
                            binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_download)
                            binding.itemActions.downloadButton.isEnabled = true
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadError.collect { uiText ->
                    createErrorDialog(uiText)
                }
            }
        }

        playerViewModel.onPlaybackRequested(lifecycleScope) { playerItems ->
            when (playerItems) {
                is PlayerViewModel.PlayerItemError -> bindPlayerItemsError(playerItems)
                is PlayerViewModel.PlayerItems -> bindPlayerItems(playerItems)
            }
        }

        binding.seriesName.setOnClickListener {
            navigateToSeries(viewModel.item.seriesId, viewModel.item.seriesName)
        }

        binding.itemActions.checkButton.setOnClickListener {
            val played = viewModel.togglePlayed()
            bindCheckButtonState(played)
        }

        binding.itemActions.favoriteButton.setOnClickListener {
            val favorite = viewModel.toggleFavorite()
            bindFavoriteButtonState(favorite)
        }

        binding.itemActions.downloadButton.setOnClickListener {
            if (viewModel.item.isDownloaded()) {
                viewModel.deleteEpisode()
                binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_download)
            } else {
                binding.itemActions.downloadButton.isEnabled = false
                binding.itemActions.downloadButton.setImageResource(android.R.color.transparent)
                binding.itemActions.progressDownload.isIndeterminate = true
                binding.itemActions.progressDownload.isVisible = true
                if (requireContext().getExternalFilesDirs(null).filterNotNull().size > 1) {
                    val storageDialog = getStorageSelectionDialog(requireContext(),
                    onItemSelected = { storageIndex ->
                        if (viewModel.item.sources.size > 1) {
                            val dialog = getVideoVersionDialog(requireContext(), viewModel.item,
                            onItemSelected = { sourceIndex ->
                                viewModel.download(sourceIndex, storageIndex)
                            },
                            onCancel = {
                                binding.itemActions.progressDownload.isVisible = false
                                binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_download)
                                binding.itemActions.downloadButton.isEnabled = true
                            })
                            dialog.show()
                            return@getStorageSelectionDialog
                        }
                        viewModel.download(storageIndex = storageIndex)
                    },
                    onCancel = {
                        binding.itemActions.progressDownload.isVisible = false
                        binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_download)
                        binding.itemActions.downloadButton.isEnabled = true
                    })
                    storageDialog.show()
                    return@setOnClickListener
                }
                if (viewModel.item.sources.size > 1) {
                    val dialog = getVideoVersionDialog(requireContext(), viewModel.item,
                    onItemSelected = { sourceIndex ->
                        viewModel.download(sourceIndex)
                    },
                    onCancel = {
                        binding.itemActions.progressDownload.isVisible = false
                        binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_download)
                        binding.itemActions.downloadButton.isEnabled = true
                    })
                    dialog.show()
                    return@setOnClickListener
                }
                viewModel.download()
            }
        }

        viewModel.loadEpisode(args.episodeId)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dialog?.let {
            val sheet = it as BottomSheetDialog
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun bindUiStateNormal(uiState: EpisodeBottomSheetViewModel.UiState.Normal) {
        uiState.apply {
            val canDownload = episode.canDownload && episode.sources.any { it.type == FindroidSourceType.REMOTE }
            val canDelete = episode.sources.any { it.type == FindroidSourceType.LOCAL }

            if (episode.playbackPositionTicks > 0) {
                binding.progressBar.layoutParams.width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (episode.playbackPositionTicks.div(episode.runtimeTicks).times(1.26)).toFloat(),
                    context?.resources?.displayMetrics
                ).toInt()
                binding.progressBar.isVisible = true
            }

            val canPlay = episode.canPlay && episode.sources.isNotEmpty()
            binding.itemActions.playButton.isEnabled = canPlay
            binding.itemActions.playButton.alpha = if (!canPlay) 0.5F else 1.0F

            bindCheckButtonState(episode.played)

            bindFavoriteButtonState(episode.favorite)

            if (episode.isDownloaded()) {
                binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_trash)
            }

            when (canDownload || canDelete) {
                true -> binding.itemActions.downloadButton.isVisible = true
                false -> binding.itemActions.downloadButton.isVisible = false
            }

            binding.episodeName.text = getString(
                CoreR.string.episode_name_extended,
                episode.parentIndexNumber,
                episode.indexNumber,
                episode.name
            )
            binding.seriesName.text = episode.seriesName
            binding.overview.text = episode.overview
            binding.year.text = formatDateTime(episode.premiereDate)
            binding.playtime.text = getString(CoreR.string.runtime_minutes, episode.runtimeTicks.div(600000000))
            binding.communityRating.isVisible = episode.communityRating != null
            binding.communityRating.text = episode.communityRating.toString()
            binding.missingIcon.isVisible = false

            bindCardItemImage(binding.episodeImage, episode)
        }
        binding.loadingIndicator.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
    }

    private fun bindUiStateError(uiState: EpisodeBottomSheetViewModel.UiState.Error) {
        binding.loadingIndicator.isVisible = false
        binding.overview.text = uiState.error.message
    }

    private fun bindPlayerItems(items: PlayerViewModel.PlayerItems) {
        navigateToPlayerActivity(items.items.toTypedArray())
        binding.itemActions.playButton.setImageDrawable(
            ContextCompat.getDrawable(
                requireActivity(),
                CoreR.drawable.ic_play
            )
        )
        binding.itemActions.progressCircular.visibility = View.INVISIBLE
    }

    private fun bindCheckButtonState(played: Boolean) {
        when (played) {
            true -> binding.itemActions.checkButton.setTintColor(CoreR.color.red, requireActivity().theme)
            false -> binding.itemActions.checkButton.setTintColorAttribute(
                MaterialR.attr.colorOnSecondaryContainer,
                requireActivity().theme
            )
        }
    }

    private fun bindFavoriteButtonState(favorite: Boolean) {
        val favoriteDrawable = when (favorite) {
            true -> CoreR.drawable.ic_heart_filled
            false -> CoreR.drawable.ic_heart
        }
        binding.itemActions.favoriteButton.setImageResource(favoriteDrawable)
        when (favorite) {
            true -> binding.itemActions.favoriteButton.setTintColor(CoreR.color.red, requireActivity().theme)
            false -> binding.itemActions.favoriteButton.setTintColorAttribute(
                MaterialR.attr.colorOnSecondaryContainer,
                requireActivity().theme
            )
        }
    }

    private fun bindPlayerItemsError(error: PlayerViewModel.PlayerItemError) {
        Timber.e(error.error.message)

        binding.playerItemsError.isVisible = true
        binding.itemActions.playButton.setImageDrawable(
            ContextCompat.getDrawable(
                requireActivity(),
                CoreR.drawable.ic_play
            )
        )
        binding.itemActions.progressCircular.visibility = View.INVISIBLE
        binding.playerItemsErrorDetails.setOnClickListener {
            ErrorDialogFragment.newInstance(error.error).show(parentFragmentManager, ErrorDialogFragment.TAG)
        }
    }

    private fun createErrorDialog(uiText: UiText) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder
            .setTitle(CoreR.string.not_enough_storage)
            .setMessage(uiText.asString(requireContext().resources))
            .setPositiveButton(getString(CoreR.string.close)) { _, _ ->
            }
        builder.show()
        binding.itemActions.progressDownload.isVisible = false
        binding.itemActions.downloadButton.setImageResource(CoreR.drawable.ic_download)
        binding.itemActions.downloadButton.isEnabled = true
    }

    private fun navigateToPlayerActivity(
        playerItems: Array<PlayerItem>,
    ) {
        findNavController().navigate(
            EpisodeBottomSheetFragmentDirections.actionEpisodeBottomSheetFragmentToPlayerActivity(
                playerItems,
            )
        )
    }

    private fun navigateToSeries(id: UUID, name: String) {
        findNavController().navigate(
            EpisodeBottomSheetFragmentDirections.actionEpisodeBottomSheetFragmentToShowFragment(
                itemId = id,
                itemName = name
            )
        )
    }

    private fun formatDateTime(datetime: DateTime?): String {
        if (datetime == null) return ""
        val instant = datetime.toInstant(ZoneOffset.UTC)
        val date = Date.from(instant)
        return DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}
