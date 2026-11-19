package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentHomeBinding;
import com.eddyizm.tempus.navigation.NavigationController;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.fragment.pager.HomePager;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.Objects;

@UnstableApi
public class HomeFragment extends Fragment implements com.eddyizm.tempus.navigation.AuraTabReselect {
    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;

    private MaterialToolbar materialToolbar;
    private AppBarLayout appBarLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();
        bind = FragmentHomeBinding.inflate(inflater, container, false);
        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initHomePager();
    }

    @Override
    public void onStart() {
        super.onStart();

        activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initAppBar() {
        appBarLayout = bind.getRoot().findViewById(R.id.toolbar_fragment);
        materialToolbar = bind.getRoot().findViewById(R.id.toolbar);
        View title = materialToolbar.findViewById(R.id.toolbar_title_container);
        title.setVisibility(View.GONE);
        View logo = bind.getRoot().findViewById(R.id.aura_home_logo);
        com.eddyizm.tempus.ui.view.AuraAppearance.applyWordmark((android.widget.TextView) logo);

        activity.setSupportActionBar(materialToolbar);
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        navigationController.setHamburgerMenuForLandscape(activity, materialToolbar);

        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(android.graphics.Color.WHITE);

        // AURA: the bar floats over the wave, see-through; a scrim appears only once the page is scrolled.
        appBarLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        appBarLayout.setStateListAnimator(null);
        appBarLayout.setElevation(0f);
        appBarLayout.setOutlineProvider(null);
        materialToolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        toolbarScrim = new android.graphics.drawable.ColorDrawable(
                com.google.android.material.color.MaterialColors.getColor(appBarLayout, com.google.android.material.R.attr.colorSurface));
        toolbarScrim.setAlpha(0);
        appBarLayout.setBackground(toolbarScrim);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, insets) -> {
            v.setPadding(v.getPaddingLeft(), activity.getStatusBarInset(), v.getPaddingRight(), v.getPaddingBottom());
            logo.setTranslationY(activity.getStatusBarInset());
            return insets;
        });
        logo.setTranslationY(activity.getStatusBarInset());
        appBarLayout.setPadding(appBarLayout.getPaddingLeft(), activity.getStatusBarInset(), appBarLayout.getPaddingRight(), appBarLayout.getPaddingBottom());
    }

    private android.graphics.drawable.ColorDrawable toolbarScrim;

    public int getToolbarHeight() {
        return appBarLayout != null ? appBarLayout.getHeight() : 0;
    }

    /** 0 = transparent bar over the wave, 1 = the page background behind the bar. */
    public void setToolbarScrim(float fraction) {
        if (toolbarScrim == null) return;
        toolbarScrim.setAlpha(Math.round(Math.max(0f, Math.min(1f, fraction)) * 235));
    }

    @Override
    public void onTabReselected() {
        if (bind == null) return;
        for (Fragment f : getChildFragmentManager().getFragments()) {
            if (f instanceof HomeTabMusicFragment) ((HomeTabMusicFragment) f).scrollToWave();
        }
    }

    private void initHomePager() {
        // AURA: only «Музыка» is left (no Podcasts, no Radio, no tab bar above the wave).
        HomePager pager = new HomePager(this);
        pager.addFragment(new HomeTabMusicFragment(), getString(R.string.home_section_music), R.drawable.ic_home);

        bind.homeViewPager.setAdapter(pager);
        bind.homeViewPager.setOffscreenPageLimit(1);
        bind.homeViewPager.setUserInputEnabled(false);
    }

    /**
     * The music library line in the toolbar belongs to the Music tab. Podcast and Radio share the
     * toolbar and are not filtered by library, so the line would name something with nothing to do
     * with what is on screen. Music is always the first page; the other two are optional.
     *
     * The toolbar reads this on every update instead of being pushed the page once, so a page the
     * pager put back on its own cannot leave the line naming the wrong tab.
     */
    public boolean isLibraryScopedTab() {
        return bind != null;
    }

    private void refreshToolbarLibraryScope() {
        ToolbarFragment toolbarFragment = (ToolbarFragment) getChildFragmentManager().findFragmentById(R.id.toolbar_fragment);
        if (toolbarFragment != null) toolbarFragment.refreshMusicLibraryIndicator();
    }
}
