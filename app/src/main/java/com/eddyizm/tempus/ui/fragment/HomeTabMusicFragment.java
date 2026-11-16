package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.common.Player;
import androidx.core.content.ContextCompat;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import com.eddyizm.tempus.hub.HubWave;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.SnapHelper;
import androidx.viewpager2.widget.ViewPager2;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentHomeTabMusicBinding;
import com.eddyizm.tempus.helper.recyclerview.CustomLinearSnapHelper;
import com.eddyizm.tempus.helper.recyclerview.DotsIndicatorDecoration;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.model.HomeSector;
import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.repository.QueueRepository;
import com.eddyizm.tempus.service.DownloaderManager;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Share;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.AlbumAdapter;
import com.eddyizm.tempus.ui.adapter.AlbumHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistAdapter;
import com.eddyizm.tempus.ui.adapter.ArtistHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.DiscoverSongAdapter;
import com.eddyizm.tempus.ui.adapter.ContinueListeningAdapter;
import com.eddyizm.tempus.ui.adapter.PlaylistHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.ShareHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.SimilarTrackAdapter;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.eddyizm.tempus.ui.adapter.YearAdapter;
import com.eddyizm.tempus.ui.dialog.HomeRearrangementDialog;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.ExternalAudioReader;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.StarredSync;
import com.eddyizm.tempus.util.TileSizeManager;
import com.eddyizm.tempus.util.UIUtil;
import com.eddyizm.tempus.viewmodel.HomeViewModel;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.eddyizm.tempus.viewmodel.PlaybackViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
public class HomeTabMusicFragment extends Fragment implements ClickCallback {
    private static final String TAG = "HomeFragment";

    private FragmentHomeTabMusicBinding bind;
    private MainActivity activity;
    private HomeViewModel homeViewModel;
    private MainViewModel mainViewModel;
    private PlaybackViewModel playbackViewModel;

    private DiscoverSongAdapter discoverSongAdapter;
    private SimilarTrackAdapter similarMusicAdapter;
    private ArtistAdapter radioArtistAdapter;
    private ArtistAdapter bestOfArtistAdapter;
    private SongHorizontalAdapter starredSongAdapter;
    private SongHorizontalAdapter topSongAdapter;
    private AlbumHorizontalAdapter starredAlbumAdapter;
    private ArtistHorizontalAdapter starredArtistAdapter;
    private AlbumAdapter recentlyAddedAlbumAdapter;
    private AlbumAdapter recentlyPlayedAlbumAdapter;
    private AlbumAdapter mostPlayedAlbumAdapter;
    private AlbumHorizontalAdapter newReleasesAlbumAdapter;
    private YearAdapter yearAdapter;
    private PlaylistHorizontalAdapter playlistHorizontalAdapter;
    private ShareHorizontalAdapter shareHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private ContinueListeningAdapter continueListeningAdapter;
    private Observer<List<Child>> bestOfObserver = null;

    // AURA_2: My Wave block
    @Nullable
    private MediaBrowser waveBrowser;
    @Nullable
    private ObjectAnimator wavePulse;
    private boolean waveLoading = false;
    private final Player.Listener waveListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updateWaveUi();
        }

        @Override
        public void onMediaItemTransition(@Nullable androidx.media3.common.MediaItem mediaItem, int reason) {
            if (bind != null && waveIsCurrent() && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                bind.homeAuraWave.auraWaveGlow.skip();
            }
            updateWaveUi();
        }
    };
    @Nullable
    private String waveCoverKey = null;
    @Nullable
    private CustomTarget<Bitmap> waveCoverTarget = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentHomeTabMusicBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        homeViewModel.clearCacheIfMusicFolderChanged();

        init();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAuraWave();

        // AURA_2: the sections are set up a few per frame after the first one, so a tab switch shows
        // Home (and its animation) at once instead of freezing while every carousel is built.
        java.util.ArrayDeque<Runnable> steps = new java.util.ArrayDeque<>();
        steps.add(() -> { initSyncStarredView(); initSyncStarredAlbumsView(); initSyncStarredArtistsView(); });
        steps.add(() -> { initDiscoverSongSlideView(); initContinueListening(); });
        steps.add(() -> { initSimilarSongView(); initArtistRadio(); initArtistBestOf(); });
        steps.add(() -> { initStarredTracksView(); initStarredAlbumsView(); initStarredArtistsView(); });
        steps.add(() -> { initMostPlayedAlbumView(); initRecentPlayedAlbumView(); initNewReleasesView(); });
        steps.add(() -> { initYearSongView(); initRecentAddedAlbumView(); initTopSongsView(); });
        steps.add(() -> {
            initPinnedPlaylistsView();
            initSharesView();
            initHomeReorganizer();
            initMusicFolderChangeListener();
            reorder();
            if (mediaBrowserListenableFuture != null) {
                if (continueListeningAdapter != null) continueListeningAdapter.setMediaBrowserFuture(mediaBrowserListenableFuture);
                if (topSongAdapter != null) setTopSongsMediaBrowserListenableFuture();
                if (starredSongAdapter != null) setStarredSongsMediaBrowserListenableFuture();
            }
        });
        runSetupSteps(bind, steps);
    }

    private void runSetupSteps(FragmentHomeTabMusicBinding owner, java.util.ArrayDeque<Runnable> steps) {
        owner.getRoot().postOnAnimation(() -> {
            if (bind != owner || steps.isEmpty()) return; // the view was destroyed meanwhile
            steps.poll().run();
            if (!steps.isEmpty()) runSetupSteps(owner, steps);
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
        attachWaveBrowser();

        if (continueListeningAdapter != null) {
            continueListeningAdapter.setMediaBrowserFuture(mediaBrowserListenableFuture);
        }

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observeStarredSongsPlayback();
        observeTopSongsPlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSharesView();
        refreshPlaylistView();
        if (topSongAdapter != null) setTopSongsMediaBrowserListenableFuture();
        if (starredSongAdapter != null) setStarredSongsMediaBrowserListenableFuture();
    }

    @Override
    public void onStop() {
        detachWaveBrowser();
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (activity != null) {
            activity.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(heroSizer);
        }
        if (hintBob != null) {
            hintBob.cancel();
            hintBob = null;
        }
        stopWavePulse();
        if (waveCoverTarget != null) {
            Glide.with(this).clear(waveCoverTarget);
            waveCoverTarget = null;
        }
        waveCoverKey = null;
        super.onDestroyView();
        bind = null;
    }

    // ---- AURA_2: My Wave ----

    private void initAuraWave() {
        bind.homeAuraWave.auraWaveButton.setOnClickListener(v -> onWaveClick());
        initWaveHero();
        updateWaveUi();
    }

    // ---- AURA: the hero fills the first screen; Home's sections wait below it ----

    private final android.view.ViewTreeObserver.OnGlobalLayoutListener heroSizer = this::sizeHero;
    @Nullable
    private ObjectAnimator hintBob;
    private int heroHeight = 0;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void initWaveHero() {
        com.eddyizm.tempus.ui.view.AuraSnapScrollView scroll = (com.eddyizm.tempus.ui.view.AuraSnapScrollView) bind.getRoot();
        int surface = com.google.android.material.color.MaterialColors.getColor(scroll, com.google.android.material.R.attr.colorSurface);
        bind.homeAuraWave.auraWaveFade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{surface & 0x00FFFFFF, surface}));

        scroll.setOnScrolled(y -> {
            applyHeroScroll(y);
            return kotlin.Unit.INSTANCE;
        });
        requireActivity().getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(heroSizer);

        // The button gives way under the finger and springs back.
        bind.homeAuraWave.auraWaveButton.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(140)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(520)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                    break;
            }
            return false;
        });

        // Entrance: the content rises softly into place.
        View content = bind.homeAuraWave.auraWaveContent;
        content.setAlpha(0f);
        content.setTranslationY(dp(36));
        content.animate().alpha(1f).translationY(0f).setStartDelay(60).setDuration(700)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();

        hintBob = ObjectAnimator.ofFloat(bind.homeAuraWave.auraWaveHintArrow, View.TRANSLATION_Y, 0f, -dp(6));
        hintBob.setDuration(950);
        hintBob.setRepeatCount(ValueAnimator.INFINITE);
        hintBob.setRepeatMode(ValueAnimator.REVERSE);
        hintBob.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        hintBob.start();
    }

    /** The hero ends where the bottom bars (tabs, mini player) begin, so the sections start below the fold. */
    private void sizeHero() {
        if (bind == null || activity == null) return;
        View scroll = bind.getRoot();
        if (!scroll.isAttachedToWindow() || scroll.getHeight() == 0) return;
        int[] loc = new int[2];
        scroll.getLocationInWindow(loc);
        int bottom = loc[1] + scroll.getHeight();
        int obstacle = bottom;
        for (int id : new int[]{R.id.bottom_navigation, R.id.player_bottom_sheet}) {
            View bar = activity.findViewById(id);
            if (bar == null || bar.getVisibility() != View.VISIBLE || bar.getHeight() == 0) continue;
            int[] l = new int[2];
            bar.getLocationInWindow(l);
            // An expanded player covers Home anyway.
            if (l[1] < loc[1] + scroll.getHeight() / 2) continue;
            if (l[1] < obstacle) obstacle = l[1];
        }
        int height = Math.max(dp(420), obstacle - loc[1]);
        View hero = bind.homeAuraWave.getRoot();
        ViewGroup.LayoutParams lp = hero.getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            hero.setLayoutParams(lp);
        }
        heroHeight = height;
        ((com.eddyizm.tempus.ui.view.AuraSnapScrollView) scroll).setSnapHeight(height);
    }

    /** Parallax: the glow and the button drift slower than the page and fade out; the top bar gains a scrim. */
    private void applyHeroScroll(int y) {
        if (bind == null) return;
        int h = heroHeight > 0 ? heroHeight : bind.homeAuraWave.getRoot().getHeight();
        if (h <= 0) return;
        float f = Math.max(0f, Math.min(1f, y / (float) h));
        View content = bind.homeAuraWave.auraWaveContent;
        content.setTranslationY(y * 0.42f);
        content.setAlpha(Math.max(0f, 1f - f * 1.6f));
        bind.homeAuraWave.auraWaveGlow.setTranslationY(y * 0.55f);
        bind.homeAuraWave.auraWaveHint.setAlpha(Math.max(0f, 0.75f * (1f - f * 4f)));
        if (getParentFragment() instanceof HomeFragment) {
            ((HomeFragment) getParentFragment()).setToolbarScrim(Math.max(0f, Math.min(1f, (f - 0.55f) / 0.4f)));
        }
    }

    /** A second tap on the Home tab brings the wave back. */
    public void scrollToWave() {
        if (bind == null) return;
        ((com.eddyizm.tempus.ui.view.AuraSnapScrollView) bind.getRoot()).snapTo(0);
    }

    private void attachWaveBrowser() {
        final ListenableFuture<MediaBrowser> future = mediaBrowserListenableFuture;
        future.addListener(() -> {
            if (future != mediaBrowserListenableFuture || !future.isDone() || future.isCancelled()) return;
            try {
                MediaBrowser browser = future.get();
                waveBrowser = browser;
                browser.addListener(waveListener);
                updateWaveUi();
            } catch (Exception e) {
                waveBrowser = null;
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void detachWaveBrowser() {
        if (waveBrowser != null) {
            waveBrowser.removeListener(waveListener);
            waveBrowser = null;
        }
    }

    /** True when the wave owns what the player is on right now. */
    private boolean waveIsCurrent() {
        MediaBrowser browser = waveBrowser;
        return browser != null
                && browser.getCurrentMediaItem() != null
                && HubWave.INSTANCE.isActive()
                && HubWave.INSTANCE.owns(browser.getCurrentMediaItem().mediaId);
    }

    /**
     * Like Yandex Music: while the wave of this app run is the current music, the button only pauses
     * or resumes it. A new wave is searched for only when nothing of the wave is current (fresh start
     * of the app, or other music was chosen meanwhile).
     */
    private void onWaveClick() {
        if (waveLoading || mediaBrowserListenableFuture == null) return;
        final ListenableFuture<MediaBrowser> future = mediaBrowserListenableFuture;
        // Decide only once the player is connected: right after switching tabs it may still be connecting.
        future.addListener(() -> {
            if (bind == null || future != mediaBrowserListenableFuture || future.isCancelled()) return;
            MediaBrowser browser;
            try {
                browser = future.get();
            } catch (Exception e) {
                return;
            }
            if (waveBrowser == null) {
                waveBrowser = browser;
                browser.addListener(waveListener);
            }
            if (waveIsCurrent()) {
                if (browser.isPlaying()) browser.pause();
                else browser.play();
                return;
            }
            startNewWave();
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void startNewWave() {
        if (waveLoading) return;
        waveLoading = true;
        updateWaveUi();
        HubWave.INSTANCE.start(mediaBrowserListenableFuture, error -> {
            waveLoading = false;
            if (bind == null) return kotlin.Unit.INSTANCE;
            if (error != null) {
                bind.homeAuraWave.auraWaveStatus.setText(error);
            } else if (activity != null) {
                activity.setBottomSheetInPeek(true);
            }
            updateWaveUi();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void updateWaveUi() {
        if (bind == null) return;
        boolean current = waveIsCurrent();
        boolean playing = current && waveBrowser != null && waveBrowser.isPlaying();

        bind.homeAuraWave.auraWaveProgress.setVisibility(waveLoading ? View.VISIBLE : View.GONE);
        bind.homeAuraWave.auraWaveIcon.setVisibility(waveLoading ? View.INVISIBLE : View.VISIBLE);
        bind.homeAuraWave.auraWaveIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (waveLoading) {
            bind.homeAuraWave.auraWaveStatus.setText(R.string.aura_wave_loading);
        } else if (current) {
            bind.homeAuraWave.auraWaveStatus.setText(playing ? R.string.aura_wave_playing : R.string.aura_wave_paused);
        } else if (!HubWave.INSTANCE.isActive()) {
            CharSequence shown = bind.homeAuraWave.auraWaveStatus.getText();
            if (shown == null || shown.length() == 0
                    || getString(R.string.aura_wave_loading).contentEquals(shown)
                    || getString(R.string.aura_wave_playing).contentEquals(shown)
                    || getString(R.string.aura_wave_paused).contentEquals(shown)) {
                bind.homeAuraWave.auraWaveStatus.setText(R.string.aura_wave_hint);
            }
        }

        if (playing) startWavePulse();
        else stopWavePulse();

        bind.homeAuraWave.auraWaveGlow.setPlaying(playing);
        updateWaveCover(current && waveBrowser != null ? waveBrowser.getCurrentMediaItem() : null);
    }

    /** The glow takes its colours from the cover of the current wave track (HomeMusic's wave hero does the same). */
    private void updateWaveCover(@Nullable androidx.media3.common.MediaItem item) {
        if (bind == null) return;
        android.net.Uri art = item != null ? item.mediaMetadata.artworkUri : null;
        String key = art != null ? art.toString() : null;
        if (java.util.Objects.equals(key, waveCoverKey)) return;
        waveCoverKey = key;
        if (waveCoverTarget != null) {
            Glide.with(this).clear(waveCoverTarget);
            waveCoverTarget = null;
        }
        if (art == null) {
            bind.homeAuraWave.auraWaveGlow.setCover(null);
            return;
        }
        waveCoverTarget = new CustomTarget<Bitmap>(24, 24) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (bind != null) bind.homeAuraWave.auraWaveGlow.setCover(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (bind != null) bind.homeAuraWave.auraWaveGlow.setCover(null);
            }
        };
        Glide.with(this).asBitmap().load(art).into(waveCoverTarget);
    }

    private void startWavePulse() {
        if (wavePulse != null || bind == null) return;
        // AURA: while the wave plays the glass button "breathes" (its rim and fill), the press keeps the scale.
        wavePulse = ObjectAnimator.ofPropertyValuesHolder(
                bind.homeAuraWave.auraWaveIcon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.8f));
        wavePulse.setDuration(1300);
        wavePulse.setRepeatCount(ValueAnimator.INFINITE);
        wavePulse.setRepeatMode(ValueAnimator.REVERSE);
        wavePulse.start();
    }

    private void stopWavePulse() {
        if (wavePulse != null) {
            wavePulse.cancel();
            wavePulse = null;
        }
        if (bind != null) {
            bind.homeAuraWave.auraWaveIcon.setScaleX(1f);
            bind.homeAuraWave.auraWaveIcon.setScaleY(1f);
            bind.homeAuraWave.auraWaveIcon.setAlpha(1f);
        }
    }

    private void init() {
        bind.discoveryTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshDiscoverySongSample(getViewLifecycleOwner());
            return true;
        });

        bind.discoveryTextViewClickable.setOnClickListener(v -> {
            homeViewModel.getRandomShuffleSample().observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);

                if (!songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                }
            });
        });

        bind.similarTracksTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshSimilarSongSample(getViewLifecycleOwner());
            return true;
        });

        bind.radioArtistTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshRadioArtistSample(getViewLifecycleOwner());
            return true;
        });

        bind.bestOfArtistTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshBestOfArtist(getViewLifecycleOwner());
            return true;
        });

        bind.starredTracksTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_STARRED, Constants.MEDIA_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_songListPageFragment, bundle);
        });

        bind.starredAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_STARRED, Constants.ALBUM_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.starredArtistsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ARTIST_STARRED, Constants.ARTIST_STARRED);
            activity.navController.navigate(R.id.action_homeFragment_to_artistListPageFragment, bundle);
        });

        bind.recentlyAddedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_RECENTLY_ADDED, Constants.ALBUM_RECENTLY_ADDED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.playlistCatalogueTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL);
            activity.navController.navigate(R.id.action_homeFragment_to_playlistCatalogueFragment, bundle);
        });

        bind.recentlyPlayedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_RECENTLY_PLAYED, Constants.ALBUM_RECENTLY_PLAYED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.mostPlayedAlbumsTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.ALBUM_MOST_PLAYED, Constants.ALBUM_MOST_PLAYED);
            activity.navController.navigate(R.id.action_homeFragment_to_albumListPageFragment, bundle);
        });

        bind.starredTracksTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshStarredTracks(getViewLifecycleOwner());
            return true;
        });

        bind.starredAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshStarredAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.starredArtistsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshStarredArtists(getViewLifecycleOwner());
            return true;
        });

        bind.recentlyPlayedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshRecentlyPlayedAlbumList(getViewLifecycleOwner());
            return true;
        });

        bind.mostPlayedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshMostPlayedAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.recentlyAddedAlbumsTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshMostRecentlyAddedAlbums(getViewLifecycleOwner());
            return true;
        });

        bind.sharesTextViewRefreshable.setOnLongClickListener(v -> {
            homeViewModel.refreshShares(getViewLifecycleOwner());
            return true;
        });

        bind.gridTracksPreTextView.setOnClickListener(view -> showPopupMenu(view, R.menu.filter_top_songs_popup_menu));
    }

    private void initSyncStarredView() {
        if (Preferences.isStarredSyncEnabled()) {
            homeViewModel.getAllStarredTracks().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> songs) {
                    if (songs != null && !songs.isEmpty()) {
                        int songsToSyncCount = 0;
                        List<String> toSyncSample = new ArrayList<>();

                        if (Preferences.getDownloadDirectoryUri() == null) {
                            DownloaderManager manager = DownloadUtil.getDownloadTracker(requireContext());
                            for (Child song : songs) {
                                if (!manager.isDownloaded(song.getId())) {
                                    songsToSyncCount++;
                                    if (toSyncSample.size() < 3) {
                                        toSyncSample.add(song.getTitle());
                                    }
                                }
                            }
                        } else {
                            for (Child song : songs) {
                                if (ExternalAudioReader.getUri(song) == null) {
                                    songsToSyncCount++;
                                    if (toSyncSample.size() < 3) {
                                        toSyncSample.add(song.getTitle());
                                    }
                                }
                            }
                        }

                        if (songsToSyncCount > 0) {
                            bind.homeSyncStarredCard.setVisibility(View.VISIBLE);

                            StringBuilder displayText = new StringBuilder();
                            if (!toSyncSample.isEmpty()) {
                                displayText.append(String.join(", ", toSyncSample));
                                if (songsToSyncCount > 3) {
                                    displayText.append("...");
                                }
                            }

                            String countText = getResources().getQuantityString(
                                    R.plurals.home_sync_starred_songs_count,
                                    songsToSyncCount,
                                    songsToSyncCount
                            );

                            if (displayText.length() > 0) {
                                bind.homeSyncStarredTracksToSync.setText(displayText.toString() + "\n" + countText);
                            } else {
                                bind.homeSyncStarredTracksToSync.setText(countText);
                            }

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> reorder());
                            }
                        } else {
                            bind.homeSyncStarredCard.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }

        bind.homeSyncStarredCancel.setOnClickListener(v -> {
            bind.homeSyncStarredCard.setVisibility(View.GONE);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> reorder());
            }
        });

        bind.homeSyncStarredDownload.setOnClickListener(v -> {
            StarredSync.syncSongs(requireContext());
            bind.homeSyncStarredCard.setVisibility(View.GONE);
            reorder();
        });
    }

    private void initSyncStarredAlbumsView() {

        if (Preferences.isStarredAlbumsSyncEnabled()) {
            homeViewModel.getStarredAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), new Observer<List<AlbumID3>>() {
                @Override
                public void onChanged(List<AlbumID3> albums) {
                    if (albums != null && !albums.isEmpty()) {
                        checkIfAlbumsNeedSync(albums);
                    }
                }
            });
        }

        bind.homeSyncStarredAlbumsCancel.setOnClickListener(v -> {
            bind.homeSyncStarredAlbumsCard.setVisibility(View.GONE);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> reorder());
            }
        });

        bind.homeSyncStarredAlbumsDownload.setOnClickListener(v -> {
            StarredSync.syncAlbums(requireContext());
            bind.homeSyncStarredAlbumsCard.setVisibility(View.GONE);
            reorder();
        });
    }

    private void checkIfAlbumsNeedSync(List<AlbumID3> albums) {
        homeViewModel.getAllStarredAlbumSongs().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> allSongs) {
                if (allSongs != null) {
                    int songsToDownload = 0;
                    List<String> albumsNeedingSync = new ArrayList<>();

                    if (Preferences.getDownloadDirectoryUri() == null) {
                        DownloaderManager manager = DownloadUtil.getDownloadTracker(requireContext());
                        
                        for (AlbumID3 album : albums) {
                            boolean albumNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getAlbumId() != null && song.getAlbumId().equals(album.getId()) &&
                                        !manager.isDownloaded(song.getId())) {
                                    songsToDownload++;
                                    albumNeedsSync = true;
                                }
                            }
                            if (albumNeedsSync) {
                                albumsNeedingSync.add(album.getName());
                            }
                        }
                    } else {
                        for (AlbumID3 album : albums) {
                            boolean albumNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getAlbumId() != null && song.getAlbumId().equals(album.getId()) &&
                                        ExternalAudioReader.getUri(song) == null) {
                                    songsToDownload++;
                                    albumNeedsSync = true;
                                }
                            }
                            if (albumNeedsSync) {
                                albumsNeedingSync.add(album.getName());
                            }
                        }
                    }

                    if (songsToDownload > 0) {
                        bind.homeSyncStarredAlbumsCard.setVisibility(View.VISIBLE);
                        
                        StringBuilder displayText = new StringBuilder();
                        List<String> sampleAlbums = new ArrayList<>();
                        
                        for (int i = 0; i < Math.min(albumsNeedingSync.size(), 3); i++) {
                            sampleAlbums.add(albumsNeedingSync.get(i));
                        }
                        
                        if (!sampleAlbums.isEmpty()) {
                            displayText.append(String.join(", ", sampleAlbums));
                            if (albumsNeedingSync.size() > 3) {
                                displayText.append("...");
                            }
                        }
                        
                        String countText = getResources().getQuantityString(
                            R.plurals.home_sync_starred_albums_count,
                            albumsNeedingSync.size(),
                            albumsNeedingSync.size()
                        );
                        
                        if (displayText.length() > 0) {
                            bind.homeSyncStarredAlbumsToSync.setText(displayText.toString() + "\n" + countText);
                        } else {
                            bind.homeSyncStarredAlbumsToSync.setText(countText);
                        }
                        
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> reorder());
                        }
                    } else {
                        bind.homeSyncStarredAlbumsCard.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    private void initSyncStarredArtistsView() {
        if (Preferences.isStarredArtistsSyncEnabled()) {
            homeViewModel.getStarredArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), new Observer<List<ArtistID3>>() {
                @Override
                public void onChanged(List<ArtistID3> artists) {
                    if (artists != null && !artists.isEmpty()) {
                        checkIfArtistsNeedSync(artists);
                    }
                }
            });
        }

        bind.homeSyncStarredArtistsCancel.setOnClickListener(v -> {
            bind.homeSyncStarredArtistsCard.setVisibility(View.GONE);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> reorder());
            }
        });

        bind.homeSyncStarredArtistsDownload.setOnClickListener(v -> {
            StarredSync.syncArtists(requireContext());
            bind.homeSyncStarredArtistsCard.setVisibility(View.GONE);
            reorder();
        });
    }

    private void checkIfArtistsNeedSync(List<ArtistID3> artists) {
        homeViewModel.getAllStarredArtistSongs().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> allSongs) {
                if (allSongs != null) {
                    int songsToDownload = 0;
                    List<String> artistsNeedingSync = new ArrayList<>();

                    if (Preferences.getDownloadDirectoryUri() == null) {
                        DownloaderManager manager = DownloadUtil.getDownloadTracker(requireContext());
                        
                        for (ArtistID3 artist : artists) {
                            boolean artistNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getArtistId() != null && song.getArtistId().equals(artist.getId()) &&
                                        !manager.isDownloaded(song.getId())) {
                                    songsToDownload++;
                                    artistNeedsSync = true;
                                }
                            }
                            if (artistNeedsSync) {
                                artistsNeedingSync.add(artist.getName());
                            }
                        }
                    } else {
                        for (ArtistID3 artist : artists) {
                            boolean artistNeedsSync = false;
                            for (Child song : allSongs) {
                                if (song.getArtistId() != null && song.getArtistId().equals(artist.getId()) &&
                                        ExternalAudioReader.getUri(song) == null) {
                                    songsToDownload++;
                                    artistNeedsSync = true;
                                }
                            }
                            if (artistNeedsSync) {
                                artistsNeedingSync.add(artist.getName());
                            }
                        }
                    }

                    if (songsToDownload > 0) {
                        bind.homeSyncStarredArtistsCard.setVisibility(View.VISIBLE);
                        
                        StringBuilder displayText = new StringBuilder();
                        List<String> sampleArtists = new ArrayList<>();
                        
                        for (int i = 0; i < Math.min(artistsNeedingSync.size(), 3); i++) {
                            sampleArtists.add(artistsNeedingSync.get(i));
                        }
                        
                        if (!sampleArtists.isEmpty()) {
                            displayText.append(String.join(", ", sampleArtists));
                            if (artistsNeedingSync.size() > 3) {
                                displayText.append("...");
                            }
                        }
                        
                        String countText = getResources().getQuantityString(
                            R.plurals.home_sync_starred_artists_count,
                            artistsNeedingSync.size(),
                            artistsNeedingSync.size()
                        );
                        
                        if (displayText.length() > 0) {
                            bind.homeSyncStarredArtistsToSync.setText(displayText.toString() + "\n" + countText);
                        } else {
                            bind.homeSyncStarredArtistsToSync.setText(countText);
                        }
                        
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> reorder());
                        }
                    } else {
                        bind.homeSyncStarredArtistsCard.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    private void initDiscoverSongSlideView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_DISCOVERY)) return;

        bind.discoverSongRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.discoverSongRecyclerView.setHasFixedSize(true);

        discoverSongAdapter = new DiscoverSongAdapter(this);
        bind.discoverSongRecyclerView.setAdapter(discoverSongAdapter);

        homeViewModel.getDiscoverSongSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            MusicUtil.ratingFilter(songs);

            if (songs == null) {
                if (bind != null) bind.homeDiscoverSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeDiscoverSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);

                discoverSongAdapter.setItems(songs);
            }
        });
    }

    private void initContinueListening() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_CONTINUE_LISTENING)) return;

        bind.continueListeningRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.continueListeningRecyclerView.setHasFixedSize(true);

        continueListeningAdapter = new ContinueListeningAdapter(mediaBrowserListenableFuture);
        bind.continueListeningRecyclerView.setAdapter(continueListeningAdapter);

        List<Preferences.ResumePoint> resumePoints = Preferences.getContinueListening();
        if (resumePoints.isEmpty()) {
            bind.homeContinueListeningSector.setVisibility(View.GONE);
        } else {
            bind.homeContinueListeningSector.setVisibility(View.VISIBLE);
            continueListeningAdapter.setItems(resumePoints);
        }

        // Sync from server bookmarks (async) so cross-device resume points appear too, then refresh.
        new QueueRepository().syncBookmarksFromServer(() -> {
            if (continueListeningAdapter == null || bind == null) return;
            List<Preferences.ResumePoint> synced = Preferences.getContinueListening();
            if (synced.isEmpty()) {
                bind.homeContinueListeningSector.setVisibility(View.GONE);
            } else {
                bind.homeContinueListeningSector.setVisibility(View.VISIBLE);
                continueListeningAdapter.setItems(synced);
            }
        });
    }

    private void initSimilarSongView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_MADE_FOR_YOU)) return;

        bind.similarTracksRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.similarTracksRecyclerView.setHasFixedSize(true);

        similarMusicAdapter = new SimilarTrackAdapter(this);
        bind.similarTracksRecyclerView.setAdapter(similarMusicAdapter);
        homeViewModel.getStarredTracksSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            MusicUtil.ratingFilter(songs);

            if (songs == null) {
                if (bind != null) bind.homeSimilarTracksSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeSimilarTracksSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);

                similarMusicAdapter.setItems(songs);
            }
        });

        CustomLinearSnapHelper similarSongSnapHelper = new CustomLinearSnapHelper();
        similarSongSnapHelper.attachToRecyclerView(bind.similarTracksRecyclerView);
    }

    private void initArtistBestOf() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_BEST_OF)) return;

        bind.bestOfArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.bestOfArtistRecyclerView.setHasFixedSize(true);

        bestOfArtistAdapter = new ArtistAdapter(this, false, true);
        bind.bestOfArtistRecyclerView.setAdapter(bestOfArtistAdapter);
        homeViewModel.getBestOfArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.homeBestOfArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeBestOfArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                bestOfArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistBestOfSnapHelper = new CustomLinearSnapHelper();
        artistBestOfSnapHelper.attachToRecyclerView(bind.bestOfArtistRecyclerView);
    }

    private void initArtistRadio() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_RADIO_STATION)) return;

        bind.radioArtistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.radioArtistRecyclerView.setHasFixedSize(true);

        radioArtistAdapter = new ArtistAdapter(this, true, false);
        bind.radioArtistRecyclerView.setAdapter(radioArtistAdapter);
        homeViewModel.getStarredArtistsSample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.homeRadioArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRadioArtistSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.afterRadioArtistDivider.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);

                radioArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper artistRadioSnapHelper = new CustomLinearSnapHelper();
        artistRadioSnapHelper.attachToRecyclerView(bind.radioArtistRecyclerView);
    }

    private void initTopSongsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_TOP_SONGS)) return;

        bind.topSongsRecyclerView.setHasFixedSize(true);

        topSongAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.topSongsRecyclerView.setAdapter(topSongAdapter);
        setTopSongsMediaBrowserListenableFuture();
        reapplyTopSongsPlayback();
        homeViewModel.getChronologySample(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), chronologies -> {
            if (chronologies == null || chronologies.isEmpty()) {
                if (bind != null) bind.homeGridTracksSector.setVisibility(View.GONE);
                if (bind != null) bind.afterGridDivider.setVisibility(View.GONE);
            } else {
                if (bind != null) bind.homeGridTracksSector.setVisibility(View.VISIBLE);
                if (bind != null) bind.afterGridDivider.setVisibility(View.VISIBLE);
                if (bind != null)
                    bind.topSongsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(chronologies.size(), 5), GridLayoutManager.HORIZONTAL, false));

                List<Child> topSongs = chronologies.stream()
                        .map(cronologia -> (Child) cronologia)
                        .collect(Collectors.toList());

                topSongAdapter.setItems(topSongs);
                reapplyTopSongsPlayback();
            }
        });

        SnapHelper topTrackSnapHelper = new PagerSnapHelper();
        topTrackSnapHelper.attachToRecyclerView(bind.topSongsRecyclerView);

        bind.topSongsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initStarredTracksView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_TRACKS)) return;

        bind.starredTracksRecyclerView.setHasFixedSize(true);

        starredSongAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.starredTracksRecyclerView.setAdapter(starredSongAdapter);
        setStarredSongsMediaBrowserListenableFuture();
        reapplyStarredSongsPlayback();
        homeViewModel.getStarredTracks(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), songs -> {
            if (songs == null) {
                if (bind != null) bind.starredTracksSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredTracksSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.starredTracksRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(songs.size(), 5), GridLayoutManager.HORIZONTAL, false));

                starredSongAdapter.setItems(songs);
                reapplyStarredSongsPlayback();
            }
        });

        SnapHelper starredTrackSnapHelper = new PagerSnapHelper();
        starredTrackSnapHelper.attachToRecyclerView(bind.starredTracksRecyclerView);

        bind.starredTracksRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initStarredAlbumsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_ALBUMS)) return;

        bind.starredAlbumsRecyclerView.setHasFixedSize(true);

        starredAlbumAdapter = new AlbumHorizontalAdapter(this, false);
        bind.starredAlbumsRecyclerView.setAdapter(starredAlbumAdapter);
        homeViewModel.getStarredAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.starredAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.starredAlbumsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(albums.size(), 5), GridLayoutManager.HORIZONTAL, false));

                starredAlbumAdapter.setItems(albums);
            }
        });

        SnapHelper starredAlbumSnapHelper = new PagerSnapHelper();
        starredAlbumSnapHelper.attachToRecyclerView(bind.starredAlbumsRecyclerView);

        bind.starredAlbumsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initStarredArtistsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_STARRED_ARTISTS)) return;

        bind.starredArtistsRecyclerView.setHasFixedSize(true);

        starredArtistAdapter = new ArtistHorizontalAdapter(this);
        bind.starredArtistsRecyclerView.setAdapter(starredArtistAdapter);
        homeViewModel.getStarredArtists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), artists -> {
            if (artists == null) {
                if (bind != null) bind.starredArtistsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.starredArtistsSector.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.afterFavoritesDivider.setVisibility(!artists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.starredArtistsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(artists.size(), 5), GridLayoutManager.HORIZONTAL, false));

                starredArtistAdapter.setItems(artists);
            }
        });

        SnapHelper starredArtistSnapHelper = new PagerSnapHelper();
        starredArtistSnapHelper.attachToRecyclerView(bind.starredArtistsRecyclerView);

        bind.starredArtistsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initNewReleasesView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_NEW_RELEASES)) return;

        bind.newReleasesRecyclerView.setHasFixedSize(true);

        newReleasesAlbumAdapter = new AlbumHorizontalAdapter(this, false);
        bind.newReleasesRecyclerView.setAdapter(newReleasesAlbumAdapter);
        homeViewModel.getRecentlyReleasedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeNewReleasesSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeNewReleasesSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.newReleasesRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(albums.size(), 5), GridLayoutManager.HORIZONTAL, false));

                newReleasesAlbumAdapter.setItems(albums);
            }
        });

        SnapHelper newReleasesSnapHelper = new PagerSnapHelper();
        newReleasesSnapHelper.attachToRecyclerView(bind.newReleasesRecyclerView);

        bind.newReleasesRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initYearSongView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_FLASHBACK)) return;

        bind.yearsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.yearsRecyclerView.setHasFixedSize(true);

        yearAdapter = new YearAdapter(this);
        bind.yearsRecyclerView.setAdapter(yearAdapter);
        homeViewModel.getYearList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), years -> {
            if (years == null) {
                if (bind != null) bind.homeFlashbackSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeFlashbackSector.setVisibility(!years.isEmpty() ? View.VISIBLE : View.GONE);

                yearAdapter.setItems(years);
            }
        });

        CustomLinearSnapHelper yearSnapHelper = new CustomLinearSnapHelper();
        yearSnapHelper.attachToRecyclerView(bind.yearsRecyclerView);
    }

    private void initMostPlayedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_MOST_PLAYED)) return;

        bind.mostPlayedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.mostPlayedAlbumsRecyclerView.setHasFixedSize(true);

        mostPlayedAlbumAdapter = new AlbumAdapter(this);
        bind.mostPlayedAlbumsRecyclerView.setAdapter(mostPlayedAlbumAdapter);
        homeViewModel.getMostPlayedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeMostPlayedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeMostPlayedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                mostPlayedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper mostPlayedAlbumSnapHelper = new CustomLinearSnapHelper();
        mostPlayedAlbumSnapHelper.attachToRecyclerView(bind.mostPlayedAlbumsRecyclerView);
    }

    private void initRecentPlayedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_LAST_PLAYED)) return;

        bind.recentlyPlayedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.recentlyPlayedAlbumsRecyclerView.setHasFixedSize(true);

        recentlyPlayedAlbumAdapter = new AlbumAdapter(this);
        bind.recentlyPlayedAlbumsRecyclerView.setAdapter(recentlyPlayedAlbumAdapter);
        homeViewModel.getRecentlyPlayedAlbumList(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeRecentlyPlayedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRecentlyPlayedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                recentlyPlayedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper recentPlayedAlbumSnapHelper = new CustomLinearSnapHelper();
        recentPlayedAlbumSnapHelper.attachToRecyclerView(bind.recentlyPlayedAlbumsRecyclerView);
    }

    private void initRecentAddedAlbumView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_RECENTLY_ADDED)) return;

        bind.recentlyAddedAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.recentlyAddedAlbumsRecyclerView.setHasFixedSize(true);

        recentlyAddedAlbumAdapter = new AlbumAdapter(this);
        bind.recentlyAddedAlbumsRecyclerView.setAdapter(recentlyAddedAlbumAdapter);
        homeViewModel.getMostRecentlyAddedAlbums(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), albums -> {
            if (albums == null) {
                if (bind != null) bind.homeRecentlyAddedAlbumsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.homeRecentlyAddedAlbumsSector.setVisibility(!albums.isEmpty() ? View.VISIBLE : View.GONE);

                recentlyAddedAlbumAdapter.setItems(albums);
            }
        });

        CustomLinearSnapHelper recentAddedAlbumSnapHelper = new CustomLinearSnapHelper();
        recentAddedAlbumSnapHelper.attachToRecyclerView(bind.recentlyAddedAlbumsRecyclerView);
    }

    private void initPinnedPlaylistsView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_PINNED_PLAYLISTS)) return;

        bind.pinnedPlaylistsRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.pinnedPlaylistsRecyclerView.setAdapter(playlistHorizontalAdapter);
        homeViewModel.getPinnedPlaylists(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) {
                if (bind != null) bind.pinnedPlaylistsSector.setVisibility(View.GONE);
            } else {
                if (bind != null)
                    bind.pinnedPlaylistsSector.setVisibility(!playlists.isEmpty() ? View.VISIBLE : View.GONE);
                if (bind != null)
                    bind.pinnedPlaylistsRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(playlists.size(), 5), GridLayoutManager.HORIZONTAL, false));

                playlistHorizontalAdapter.setItems(playlists);
            }
        });

        SnapHelper pinnedPlaylistsSnapHelper = new PagerSnapHelper();
        pinnedPlaylistsSnapHelper.attachToRecyclerView(bind.pinnedPlaylistsRecyclerView);

        bind.pinnedPlaylistsRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initSharesView() {
        if (homeViewModel.checkHomeSectorVisibility(Constants.HOME_SECTOR_SHARED)) return;

        bind.sharesRecyclerView.setHasFixedSize(true);

        shareHorizontalAdapter = new ShareHorizontalAdapter(this);
        bind.sharesRecyclerView.setAdapter(shareHorizontalAdapter);
        if (Preferences.isSharingEnabled()) {
            homeViewModel.getShares(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), shares -> {
                if (shares == null) {
                    if (bind != null) bind.sharesSector.setVisibility(View.GONE);
                } else {
                    if (bind != null)
                        bind.sharesSector.setVisibility(!shares.isEmpty() ? View.VISIBLE : View.GONE);
                    if (bind != null)
                        bind.sharesRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), UIUtil.getSpanCount(shares.size(), 10), GridLayoutManager.HORIZONTAL, false));

                    shareHorizontalAdapter.setItems(shares);
                }
            });
        }

        SnapHelper starredTrackSnapHelper = new PagerSnapHelper();
        starredTrackSnapHelper.attachToRecyclerView(bind.sharesRecyclerView);

        bind.sharesRecyclerView.addItemDecoration(
                new DotsIndicatorDecoration(
                        getResources().getDimensionPixelSize(R.dimen.radius),
                        getResources().getDimensionPixelSize(R.dimen.radius) * 4,
                        getResources().getDimensionPixelSize(R.dimen.dots_height),
                        requireContext().getResources().getColor(R.color.titleTextColor, null),
                        requireContext().getResources().getColor(R.color.titleTextColor, null))
        );
    }

    private void initMusicFolderChangeListener() {
        mainViewModel.getActiveMusicFolderId().observe(getViewLifecycleOwner(), musicFolderId ->
                homeViewModel.reloadIfMusicFolderChanged(getViewLifecycleOwner()));
    }

    private void initHomeReorganizer() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> {
            if (bind != null) bind.homeSectorRearrangementButton.setVisibility(View.VISIBLE);
        };
        handler.postDelayed(runnable, 5000);

        bind.homeSectorRearrangementButton.setOnClickListener(v -> {
            HomeRearrangementDialog dialog = new HomeRearrangementDialog();
            dialog.show(requireActivity().getSupportFragmentManager(), null);
        });
    }

    private void refreshSharesView() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> {
            if (getView() != null && bind != null && Preferences.isSharingEnabled()) {
                homeViewModel.refreshShares(getViewLifecycleOwner());
            }
        };
        handler.postDelayed(runnable, 100);
    }

    private void setSlideViewOffset(ViewPager2 viewPager, float pageOffset, float pageMargin) {
        viewPager.setPageTransformer((page, position) -> {
            float myOffset = position * -(2 * pageOffset + pageMargin);
            if (viewPager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL) {
                if (ViewCompat.getLayoutDirection(viewPager) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    page.setTranslationX(-myOffset);
                } else {
                    page.setTranslationX(myOffset);
                }
            } else {
                page.setTranslationY(myOffset);
            }
        });
    }

    // True while a coalesced home-tree rebuild is already posted, so the burst of reorder()
    // calls that the ~24 async section loads fire at app open collapses into one rebuild/frame.
    private boolean reorderRequested = false;

    public void reorder() {
        // One rebuild per frame at most. Every section load that finishes calls this, and each
        // call used to removeAllViews() + re-add the whole Home tree (~17 nested RecyclerViews).
        // The short-circuit below skips even that when the visible set hasn't changed.
        //
        // Note for the next reader: I first sold this as the fix for the "isn't responding" ANR
        // on app open, and that was wrong. The ANRs kept happening after this landed, and an
        // instrumented build never logged a single reorder() on the reproduce path. The Home tab
        // is drowning in memory pressure from full-resolution cover art, which is a separate
        // problem. This just stops doing the same rebuild many times over - a cheap win, not the
        // ANR fix.
        if (bind == null || homeViewModel.getHomeSectorList() == null) return;
        if (reorderRequested) return;
        reorderRequested = true;
        bind.homeLinearLayoutContainer.post(() -> {
            reorderRequested = false;
            reorderNow();
        });
    }

    private void reorderNow() {
        if (bind == null || homeViewModel.getHomeSectorList() == null) return;

        ArrayList<View> desired = buildDesiredOrder();

        // No change to the visible/ordered set -> nothing to rebuild. Section adapters already
        // update their own rows via setItems(); a reorder is only needed when the set of visible
        // sector views (or their order) changes.
        ViewGroup container = bind.homeLinearLayoutContainer;
        if (container.getChildCount() == desired.size()) {
            boolean unchanged = true;
            for (int i = 0; i < desired.size(); i++) {
                if (container.getChildAt(i) != desired.get(i)) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged) return;
        }

        container.removeAllViews();
        for (View v : desired) {
            container.addView(v);
        }
    }

    private ArrayList<View> buildDesiredOrder() {
        ArrayList<View> desired = new ArrayList<>();

        // AURA_2: My Wave always leads Home.
        desired.add(bind.homeAuraWave.getRoot());

        if (bind.homeSyncStarredCard.getVisibility() == View.VISIBLE) {
            desired.add(bind.homeSyncStarredCard);
        }

        if (bind.homeSyncStarredAlbumsCard.getVisibility() == View.VISIBLE) {
            desired.add(bind.homeSyncStarredAlbumsCard);
        }

        if (bind.homeSyncStarredArtistsCard.getVisibility() == View.VISIBLE) {
            desired.add(bind.homeSyncStarredArtistsCard);
        }

        for (HomeSector sector : homeViewModel.getHomeSectorList()) {
            if (!sector.isVisible()) continue;

            switch (sector.getId()) {
                case Constants.HOME_SECTOR_DISCOVERY:
                    desired.add(bind.homeDiscoverSector);
                    break;
                case Constants.HOME_SECTOR_CONTINUE_LISTENING:
                    desired.add(bind.homeContinueListeningSector);
                    break;
                case Constants.HOME_SECTOR_MADE_FOR_YOU:
                    desired.add(bind.homeSimilarTracksSector);
                    break;
                case Constants.HOME_SECTOR_BEST_OF:
                    desired.add(bind.homeBestOfArtistSector);
                    break;
                case Constants.HOME_SECTOR_RADIO_STATION:
                    desired.add(bind.homeRadioArtistSector);
                    break;
                case Constants.HOME_SECTOR_TOP_SONGS:
                    desired.add(bind.homeGridTracksSector);
                    break;
                case Constants.HOME_SECTOR_STARRED_TRACKS:
                    desired.add(bind.starredTracksSector);
                    break;
                case Constants.HOME_SECTOR_STARRED_ALBUMS:
                    desired.add(bind.starredAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_STARRED_ARTISTS:
                    desired.add(bind.starredArtistsSector);
                    break;
                case Constants.HOME_SECTOR_NEW_RELEASES:
                    desired.add(bind.homeNewReleasesSector);
                    break;
                case Constants.HOME_SECTOR_FLASHBACK:
                    desired.add(bind.homeFlashbackSector);
                    break;
                case Constants.HOME_SECTOR_MOST_PLAYED:
                    desired.add(bind.homeMostPlayedAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_LAST_PLAYED:
                    desired.add(bind.homeRecentlyPlayedAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_RECENTLY_ADDED:
                    desired.add(bind.homeRecentlyAddedAlbumsSector);
                    break;
                case Constants.HOME_SECTOR_PINNED_PLAYLISTS:
                    desired.add(bind.pinnedPlaylistsSector);
                    break;
                case Constants.HOME_SECTOR_SHARED:
                    desired.add(bind.sharesSector);
                    break;
            }
        }

        desired.add(bind.homeSectorRearrangementButton);
        return desired;
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_last_week_name) {
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 0);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_week));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_last_month_name) {
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 1);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_month));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_last_year_name) {
                homeViewModel.changeChronologyPeriod(getViewLifecycleOwner(), 2);
                bind.gridTracksPreTextView.setText(getString(R.string.home_title_last_year));
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void refreshPlaylistView() {
        final Handler handler = new Handler();

        final Runnable runnable = () -> {
            if (getView() != null && bind != null && homeViewModel != null)
                homeViewModel.refreshPinnedPlaylists();
        };

        handler.postDelayed(runnable, 100);
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    public void onMediaClick(Bundle bundle) {
        if (bundle.containsKey(Constants.MEDIA_MIX)) {
            Child track = bundle.getParcelable(Constants.TRACK_OBJECT);
            activity.setBottomSheetInPeek(true);

            if (mediaBrowserListenableFuture != null) {
                final boolean[] playbackStarted = {false};
                Toast.makeText(requireContext(), R.string.bottom_sheet_generating_instant_mix, Toast.LENGTH_SHORT).show();
                homeViewModel.getMediaInstantMix(getViewLifecycleOwner(), track)
                        .observe(getViewLifecycleOwner(), songs -> {
                            if (playbackStarted[0] || songs == null || songs.isEmpty()) return;

                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (playbackStarted[0]) return;

                                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                                playbackStarted[0] = true;
                            }, 300);
                        });
            }
        } else if (bundle.containsKey(Constants.MEDIA_CHRONOLOGY)) {
            List<Child> media = bundle.getParcelableArrayList(Constants.TRACKS_OBJECT);
            MediaManager.startQueue(mediaBrowserListenableFuture, media, bundle.getInt(Constants.ITEM_POSITION));
            activity.setBottomSheetInPeek(true);
        } else {
            MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
            activity.setBottomSheetInPeek(true);
        }
        topSongAdapter.notifyDataSetChanged();
        starredSongAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        if (bundle.containsKey(Constants.MEDIA_MIX) && bundle.getBoolean(Constants.MEDIA_MIX)) {
            Snackbar.make(activity.bind.playerBottomSheet, R.string.artist_adapter_radio_station_starting, Snackbar.LENGTH_LONG)
                    .setAnchorView(activity.getSnackbarAnchor())
                    .show();

            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getArtistInstantMix(getViewLifecycleOwner(), bundle.getParcelable(Constants.ARTIST_OBJECT)).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);

                    if (songs != null && !songs.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    }
                });
            }
        } else if (bundle.containsKey(Constants.MEDIA_BEST_OF) && bundle.getBoolean(Constants.MEDIA_BEST_OF)) {
            ArtistID3 artist = bundle.getParcelable(Constants.ARTIST_OBJECT);
            if (artist == null) return;
            if (mediaBrowserListenableFuture != null) {
                homeViewModel.getArtistBestOf(artist).observe(getViewLifecycleOwner(), songs -> {
                    MusicUtil.ratingFilter(songs);
                    if (songs != null && !songs.isEmpty()) {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    }
                });
            }
        } else {
            Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
        }
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }

    @Override
    public void onYearClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songListPageFragment, bundle);
    }

    @Override
    public void onShareClick(Bundle bundle) {
        Share share = bundle.getParcelable(Constants.SHARE_OBJECT);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(share.getUrl())).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
    }

    @Override
    public void onPlaylistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.playlistRowBottomSheetDialog, bundle);
    }


    @Override
    public void onShareLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.shareBottomSheetDialog, bundle);
    }

    private void observeStarredSongsPlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (starredSongAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                starredSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (starredSongAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                starredSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void observeTopSongsPlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (topSongAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                topSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (topSongAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                topSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyStarredSongsPlayback() {
        if (starredSongAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            starredSongAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void reapplyTopSongsPlayback() {
        if (topSongAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            topSongAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void setTopSongsMediaBrowserListenableFuture() {
        topSongAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void setStarredSongsMediaBrowserListenableFuture() {
        starredSongAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }
}
