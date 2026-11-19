package com.eddyizm.tempus.navigation;

import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.ui.view.AuraAppearance;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigation.NavigationView;

import org.jetbrains.annotations.Contract;

public class NavigationHelper {
    /* UI components */
    private BottomNavigationView bottomNavigationView;
    private FrameLayout bottomNavigationViewFrame;
    private DrawerLayout drawerLayout;

    /* Navigation components */
    private NavigationView navigationView;
    private NavHostFragment navHostFragment;
    private NavController navController;

    /* States that need to be remembered */
    // -- //

    /* Private constructor */
    public NavigationHelper(@NonNull BottomNavigationView bottomNavigationView,
                            @NonNull FrameLayout bottomNavigationViewFrame,
                            @NonNull DrawerLayout drawerLayout,
                            @NonNull NavigationView navigationView,
                            @NonNull NavHostFragment navHostFragment,
                            @NonNull NavController navController) {
        this.bottomNavigationView = bottomNavigationView;
        this.bottomNavigationViewFrame = bottomNavigationViewFrame;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.navHostFragment = navHostFragment;
        this.navController = navController;
    }

    public void syncWithBottomSheetBehavior(@NonNull BottomSheetBehavior<View> bottomSheetBehavior,
                                            @NonNull NavController navController) {
        bottomNavigationView.setItemActiveIndicatorEnabled(false);
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    applyScreenBackground(destination);
                    bottomNavigationView.setItemIconTintList(AuraAppearance.navigationTint(bottomNavigationView.getContext()));

                    // React to the user clicking one of these on bottom-navbar/drawer
                    boolean isTarget = isTargetDestination(destination);
                    int currentState = bottomSheetBehavior.getState();

                    if (isTarget && currentState == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                });

        NavigationUI.setupWithNavController(bottomNavigationView, navController);
        NavigationUI.setupWithNavController(navigationView, navController);

        // AURA: tapping the tab that is already open must not build its screen again (the Hub page
        // used to vanish that way); the screen is told instead, and may scroll to its top.
        bottomNavigationView.setOnItemReselectedListener(item -> {
            androidx.fragment.app.Fragment current = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
            if (current instanceof AuraTabReselect) ((AuraTabReselect) current).onTabReselected();
        });
    }

    public void leaveHub() {
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() == R.id.homeFragment) return;
        if (!navController.popBackStack(R.id.homeFragment, false)) {
            navController.navigate(R.id.homeFragment, null, new androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getId(), false).setLaunchSingleTop(true).build());
        }
    }

    /**
     * AURA_2: Home keeps the theme background; every other screen gets the quiet AURA gradient.
     * Screens with their own opaque root already carry the same gradient.
     */
    private void applyScreenBackground(@NonNull NavDestination destination) {
        View host = navHostFragment.getView();
        if (host == null) return;
        int destId = destination.getId();
        boolean plain = destId == R.id.homeFragment
                || destId == R.id.hubFragment
                || destId == R.id.landingFragment
                || destId == R.id.loginFragment;
        // The gradient stays on the host; only its alpha moves, so a screen change crossfades the background.
        android.graphics.drawable.Drawable bg = host.getBackground();
        if (bg == null || host.getTag(R.id.nav_host_fragment) != Boolean.TRUE) {
            bg = androidx.core.content.ContextCompat.getDrawable(host.getContext(), R.drawable.aura_screen_gradient);
            if (bg == null) return;
            bg = bg.mutate();
            bg.setAlpha(plain ? 0 : 255);
            host.setBackground(bg);
            host.setTag(R.id.nav_host_fragment, Boolean.TRUE);
            return;
        }
        int target = plain ? 0 : 255;
        if (backgroundFade != null) backgroundFade.cancel();
        if (bg.getAlpha() == target) return;
        backgroundFade = android.animation.ObjectAnimator.ofInt(bg, "alpha", bg.getAlpha(), target);
        backgroundFade.setDuration(320);
        backgroundFade.setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator());
        backgroundFade.start();
    }

    @androidx.annotation.Nullable
    private android.animation.ObjectAnimator backgroundFade;

    @Contract(pure = true)
    private static boolean isTargetDestination(NavDestination destination) {
        int destId = destination.getId();
        return destId == R.id.homeFragment ||
                destId == R.id.libraryFragment ||
                destId == R.id.downloadFragment ||
                destId == R.id.auraSearchFragment ||
                destId == R.id.hubFragment ||
                destId == R.id.albumCatalogueFragment ||
                destId == R.id.artistCatalogueFragment ||
                destId == R.id.genreCatalogueFragment ||
                destId == R.id.playlistCatalogueFragment;
    }

    /*
    Clean public methods
    Removes the need to invoke the activity on the fragment
     */

    public void setBottomNavigationBarVisibility(boolean visible) {
        int visibility = visible
                ? View.VISIBLE
                : View.GONE;
        bottomNavigationView.setVisibility(visibility);
        bottomNavigationViewFrame.setVisibility(visibility);
    }

    public void setNavigationDrawerLock(boolean locked) {
        int mode = locked
                ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                : DrawerLayout.LOCK_MODE_UNLOCKED;
        drawerLayout.setDrawerLockMode(mode);
    }

    public boolean isNavigationDrawerLocked() {
        return drawerLayout.getDrawerLockMode(navigationView) != DrawerLayout.LOCK_MODE_UNLOCKED;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void toggleNavigationDrawerLockOnOrientationChange(
            AppCompatActivity activity) {

        int orientation = activity.getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (Preferences.getEnableDrawerOnPortrait()) {
            setNavigationDrawerLock(false);
            return;
        }
        setNavigationDrawerLock(!isLandscape);
    }

    public void sethamburgerMenuIconOnToolbar(AppCompatActivity activity, MaterialToolbar toolbar) {

        int orientation = activity.getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                    activity,
                    drawerLayout,
                    toolbar,
                    R.string.toolbar_navigation_drawer_open,
                    R.string.toolbar_navigation_drawer_closed
            );
            drawerLayout.addDrawerListener(drawerToggle);
            drawerToggle.syncState();
        }
    }

    /*
    All of these are the "backward compatible" changes that don't break the assumption
    that everything was defined on the activity and is gobally available
     */

    @NonNull
    public NavController getNavController() {
        return navController;
    }

    @NonNull
    public BottomNavigationView getBottomNavigationView() {
        return bottomNavigationView;
    }

    @NonNull
    public FrameLayout getBottomNavigationViewFrame() {
        return bottomNavigationViewFrame;
    }

    @NonNull
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    /*
    Auxiliar functions, could be moved somewhere else
     */

    @OptIn(markerClass = UnstableApi.class)
    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        WindowInsetsControllerCompat insetsController;
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        insetsController = new WindowInsetsControllerCompat(window, decorView);

        if (visibility) {
            insetsController.show(WindowInsetsCompat.Type.navigationBars());
            insetsController.show(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        } else {
            insetsController.hide(WindowInsetsCompat.Type.navigationBars());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
