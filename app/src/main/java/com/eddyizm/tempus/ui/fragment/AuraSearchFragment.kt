package com.eddyizm.tempus.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.search.AuraPalette
import com.eddyizm.tempus.ui.search.AuraSearchScreen
import com.eddyizm.tempus.ui.search.AuraSearchViewModel
import com.google.android.material.color.MaterialColors

/**
 * AURA «Поиск» tab (it replaced «Библиотека»): the hub's search page, ported to the app. Songs the
 * library has play in the app's player; the rest go to the hub's download queue.
 */
@UnstableApi
class AuraSearchFragment : Fragment() {
    private lateinit var vm: AuraSearchViewModel
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!vm.back()) isEnabled = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        vm = ViewModelProvider(requireActivity())[AuraSearchViewModel::class.java]
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        val context = requireContext()
        val fg = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF.toInt())
        val palette = AuraPalette(
            fg = Color(fg),
            muted = Color(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFB7B2C8.toInt())),
            accent = Color(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0xFFB9A4FF.toInt())),
            onAccent = Color(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xFF1C0F47.toInt())),
            card = Color(fg).copy(alpha = 0.08f),
            line = Color(fg).copy(alpha = 0.10f),
        )
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LaunchedEffect(vm.pages.size) { backCallback.isEnabled = vm.pages.size > 1 }
                AuraSearchScreen(vm, palette, ::play)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val activity = activity as? MainActivity ?: return
        activity.toggleBottomNavigationBarVisibilityOnOrientationChange()
        activity.setBottomSheetVisibility(true)
    }

    private fun play(songs: List<Child>, index: Int) {
        val activity = activity as? MainActivity ?: return
        MediaManager.startQueue(activity.mediaBrowserListenableFuture, songs, index)
        activity.setBottomSheetInPeek(true)
    }
}
