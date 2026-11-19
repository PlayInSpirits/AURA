package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.model.HomeSector;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Preferences;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class HomeRearrangementViewModel extends AndroidViewModel {
    private List<HomeSector> sectors = new ArrayList<>();

    public HomeRearrangementViewModel(@NonNull Application application) {
        super(application);
    }

    public List<HomeSector> getHomeSectorList() {
        if (sectors != null && !sectors.isEmpty()) return sectors;

        sectors = loadHomeSectors(getApplication());
        return sectors;
    }

    public static List<HomeSector> loadHomeSectors(Application application) {
        String saved = Preferences.getHomeSectorList();
        List<HomeSector> sectors = saved == null ? null : new Gson().fromJson(saved,
                new TypeToken<List<HomeSector>>() { }.getType());
        if (sectors == null) return fillStandardHomeSectorList(application);
        sectors.removeIf(sector -> switch (sector.getId()) {
            case Constants.HOME_SECTOR_MADE_FOR_YOU, Constants.HOME_SECTOR_STARRED_TRACKS,
                    Constants.HOME_SECTOR_FLASHBACK -> true;
            default -> false;
        });
        if (sectors.stream().noneMatch(sector -> Constants.HOME_SECTOR_CONTINUE_LISTENING.equals(sector.getId()))) {
            sectors.add(new HomeSector(Constants.HOME_SECTOR_CONTINUE_LISTENING,
                    application.getString(R.string.home_title_continue_listening), true, 2));
        }
        return sectors;
    }

    public void orderSectorLiveListAfterSwap(List<HomeSector> sectors) {
        this.sectors = sectors;
    }

    public void saveHomeSectorList(List<HomeSector> sectors) {
        Preferences.setHomeSectorList(sectors);
    }

    public void resetHomeSectorList() {
        Preferences.setHomeSectorList(null);
    }

    public void closeDialog() {
        sectors = null;
    }

    private static List<HomeSector> fillStandardHomeSectorList(Application application) {
        List<HomeSector> sectors = new ArrayList<>();

        sectors.add(new HomeSector(Constants.HOME_SECTOR_DISCOVERY, application.getString(R.string.home_title_discovery), true, 1));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_CONTINUE_LISTENING, application.getString(R.string.home_title_continue_listening), true, 2));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_PINNED_PLAYLISTS, application.getString(R.string.home_title_pinned_playlists), true, 3));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ALBUMS, application.getString(R.string.home_title_starred_albums), true, 4));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_STARRED_ARTISTS, application.getString(R.string.home_title_starred_artists), true, 5));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_TOP_SONGS, application.getString(R.string.home_title_top_songs), true, 6));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_BEST_OF, application.getString(R.string.home_title_best_of), true, 7));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_SHARED, application.getString(R.string.home_title_shares), true, 8));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RADIO_STATION, application.getString(R.string.home_title_radio_station), false, 9));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_NEW_RELEASES, application.getString(R.string.home_title_new_releases), false, 10));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_MOST_PLAYED, application.getString(R.string.home_title_most_played), false, 11));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_LAST_PLAYED, application.getString(R.string.home_title_last_played), false, 12));
        sectors.add(new HomeSector(Constants.HOME_SECTOR_RECENTLY_ADDED, application.getString(R.string.home_title_recently_added), false, 13));

        return sectors;
    }
}
