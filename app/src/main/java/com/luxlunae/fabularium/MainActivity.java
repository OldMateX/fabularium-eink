/*
 * Copyright (C) 2018 Tim Cadogan-Cowper.
 *
 * This file is part of Fabularium.
 *
 * Fabularium is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fabularium; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.luxlunae.fabularium;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import androidx.appcompat.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.Toast;
import androidx.preference.PreferenceManager;

import com.luxlunae.fabularium.create.ProjectSelectorFragment;
import com.luxlunae.fabularium.explore.FileListAdapter;
import com.luxlunae.fabularium.explore.FileSelectorFragment;
import com.luxlunae.fabularium.play.GameSelectorFragment;
import com.luxlunae.glk.GLKConstants;

import java.io.File;
import java.io.IOException;

/**
 * Fabularium's main entry class.
 */
public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, FileListAdapter.ToolbarListener, AsyncProgressFragment.CopyAssetsCallback {
    public final static String GAME_PATH = "com.luxlunae.fabularium.GAME_PATH";
    public final static String GAME_FORMAT = "com.luxlunae.fabularium.GAME_FORMAT";
    public final static String GAME_IFID = "com.luxlunae.fabularium.GAME_IFID";
    public final static String USER_PREFS = "com.luxlunae.fabularium.USER_PREFS";
    public final static String DEFAULT_BITMAP = "com.luxlunae.fabularium.DEFAULT_BITMAP";
    public static final int DB_LOADER = 0;
    public static final int DB_LOADER_PROJECTS = 1;
    public static final int POS_TAB_PLAY = 0;
    public static final int POS_TAB_EXPLORE = 1;
    public static final int POS_TAB_CREATE = 2;
    private static final boolean DEBUG_LIFECYCLE = false;
    private static final int REQUEST_EXT_STORAGE = 0;
    private static final int NUM_TABS = 3;
    private int mTabCurPos = POS_TAB_PLAY;

    private ScreenSlidePagerAdapter mAdapter;
    private ViewPager mPager;
    private Toolbar mToolbar;

    private CheckBox mToolbarCheckBox;
    private Button mToolbarButOK;
    private Button mToolbarButCancel;

    @NonNull
    private TOOLBAR_MODE mToolbarMode = TOOLBAR_MODE.SELECT_FILES;
    @Nullable
    private CompoundButton.OnCheckedChangeListener mCheckboxListener;
    private int mNumSelected = 0;

    // Volume-key page navigation: page through grids/lists, or scroll dialogs
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                android.view.View root = findViewById(android.R.id.content);
                int direction = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) ? 1 : -1;

                // 1. Try any visible GridView (Play, Create tabs)
                android.widget.GridView grid = findFirstVisibleGridView(root);
                if (grid != null && grid.getCount() > 0) {
                    int numCols = grid.getNumColumns();
                    int current = grid.getFirstVisiblePosition();
                    int rowsPerPage = 3;
                    int target = current + (direction * rowsPerPage * numCols);
                    grid.setSelection(Math.max(0, Math.min(target, grid.getCount() - 1)));
                    return true;
                }

                // 2. Try any visible ListView (Explore tab)
                android.widget.ListView list = findFirstVisibleListView(root);
                if (list != null && list.getCount() > 0) {
                    int current = list.getFirstVisiblePosition();
                    int itemsPerPage = 8;
                    int target = current + (direction * itemsPerPage);
                    list.setSelection(Math.max(0, Math.min(target, list.getCount() - 1)));
                    return true;
                }

                // 3. Try any visible ScrollView (metadata dialog, help, etc.)
                android.widget.ScrollView scroll = findFirstVisibleScrollView(root);
                if (scroll != null) {
                    int scrollAmount = scroll.getHeight();
                    scroll.smoothScrollBy(0, direction * scrollAmount);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private android.widget.GridView findFirstVisibleGridView(android.view.View parent) {
        if (parent instanceof android.widget.GridView) {
            if (parent.getVisibility() == android.view.View.VISIBLE) return (android.widget.GridView) parent;
        }
        if (parent instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.GridView found = findFirstVisibleGridView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private android.widget.ListView findFirstVisibleListView(android.view.View parent) {
        if (parent instanceof android.widget.ListView) {
            if (parent.getVisibility() == android.view.View.VISIBLE) return (android.widget.ListView) parent;
        }
        if (parent instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ListView found = findFirstVisibleListView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private android.widget.ScrollView findFirstVisibleScrollView(android.view.View parent) {
        if (parent instanceof android.widget.ScrollView) {
            if (parent.getVisibility() == android.view.View.VISIBLE) return (android.widget.ScrollView) parent;
        }
        if (parent instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) parent;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ScrollView found = findFirstVisibleScrollView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        FabLogger.initialise(this);
        if (DEBUG_LIFECYCLE) {
            FabLogger.debug("MainActivity: onCreate()");
        }

        // Apply theme before super.onCreate()
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = prefs.getBoolean("pref_dark_mode", false);
        boolean eink = prefs.getBoolean("pref_eink_mode", false);

        AppCompatDelegate.setDefaultNightMode(
            dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        setTheme(eink ? R.style.Theme_Fabularium_EInk : R.style.Theme_Fabularium);
        if (eink) {
            getWindow().setWindowAnimations(0);
        }   

        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.main_layout);

        // Ensure we have the permissions we need
        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // runtime permissions apply for Android M and above only
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Explain why we need the permission
                FabLogger.warn("Displaying external storage permission rationale to provide additional context.");

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Snackbar sb = Snackbar.make(findViewById(R.id.mainContent),
                        R.string.permission_rationale_extstorage, Snackbar.LENGTH_INDEFINITE);
                sb.setAction(R.string.dialog_ok, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXT_STORAGE);
                    }
                });
                sb.show();
            } else {
                // No explanation needed, just request the permission
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXT_STORAGE);
            }
        } else {
            copyCoreAssets();
            createTabStrip();
        }
    }

    @Override
    public void onPause() {
        if (DEBUG_LIFECYCLE) {
            FabLogger.debug("MainActivity: onPause()");
        }
        FabLogger.flush();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = prefs.getBoolean("pref_dark_mode", false);
        int expected = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != expected) {
            AppCompatDelegate.setDefaultNightMode(expected);
        }
    }   

    @Override
    public void onStop() {
        if (DEBUG_LIFECYCLE) {
            FabLogger.debug("MainActivity: onStop()");
        }
        FabLogger.shutdown();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (DEBUG_LIFECYCLE) {
            FabLogger.debug("MainActivity: onDestroy()");
        }
        FabLogger.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_EXT_STORAGE) {
            FabLogger.debug("Received response for external storage permission request.");
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                FabLogger.debug("External storage permission granted.");
                copyCoreAssets();
                createTabStrip();
            } else {
                FabLogger.debug("External storage permission NOT granted.");
                Toast.makeText(this,
                        R.string.permission_extstorage_denied_app_exit,
                        Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onBackPressed() {
        if (DEBUG_LIFECYCLE) {
            FabLogger.debug("MainActivity: onBackPressed()");
        }
        boolean ret = false;

        if (mTabCurPos == POS_TAB_EXPLORE) {
            Fragment frag = getSupportFragmentManager().findFragmentByTag("tab_" + POS_TAB_EXPLORE);
            if (frag instanceof FileSelectorFragment) {
                ret = ((FileSelectorFragment) frag).onBackPressed();
            }
        }

        if (!ret) {
            super.onBackPressed();
        }
    }   

    @Override
    public void onCopyAssetsPostExecute(@Nullable File[] results) {
    }

    public void copyCoreAssets() {
        String[] assetDirs;
        try {
            assetDirs = new String[]{"/inform/lib", GLKConstants.getDir(this, GLKConstants.SUBDIR.LIB_INFORM),
                    "/inform/include", GLKConstants.getDir(this, GLKConstants.SUBDIR.INCLUDE_INFORM),
                    "/tads3/lib", GLKConstants.getDir(this, GLKConstants.SUBDIR.LIB_TADS3),
                    "/tads3/include", GLKConstants.getDir(this, GLKConstants.SUBDIR.INCLUDE_TADS3),
                    "/adrift/StandardLibrary.amf", GLKConstants.getDir(this, GLKConstants.SUBDIR.LIB_ADRIFT).concat("StandardLibrary.amf"),
                    "/fab.ini", GLKConstants.getDir(this, null).concat("fab.ini"),
                    "/keyboards.ini", GLKConstants.getDir(this, null).concat("keyboards.ini")};

            // (Re-)create and populate any missing asset directories
            AsyncProgressFragment f = new AsyncProgressFragment();
            getSupportFragmentManager().beginTransaction().add(f, "frag_copyassets").commitAllowingStateLoss();
            f.copyAssets(getResources(), this, getResources().getAssets(), assetDirs);
        } catch (@NonNull IOException | SecurityException e) {
            Toast.makeText(this,
                    "Could not create one or more assets. Please check you have granted file read/write permissions and try again. If you have overridden any default paths in your settings, also check Fabularium can read/write to those paths.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void createTabStrip() {
        mAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager = findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mPager);
        mPager.setPageTransformer(false, (v, position) -> {
            v.setTranslationX(0);
        });   

        mToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(mToolbar);
        mToolbar.setPopupTheme(R.style.Theme_Fabularium_Popup);   

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }   



    @NonNull
    public Fragment getTab(int pos) {
        return getSupportFragmentManager().findFragmentByTag("tab_" + pos);
    }   

    public void setToolbarCheckBoxListener(@Nullable CompoundButton.OnCheckedChangeListener listener) {
        if (mToolbarCheckBox != null) {
            mToolbarCheckBox.setOnCheckedChangeListener(listener);
            mCheckboxListener = listener;
        }
    }

    public void setToolbarOKListener(@Nullable View.OnClickListener listener) {
        if (mToolbarButOK != null) {
            mToolbarButOK.setOnClickListener(listener);
        }
    }

    public void setToolbarCancelListener(@Nullable View.OnClickListener listener) {
        if (mToolbarButCancel != null) {
            mToolbarButCancel.setOnClickListener(listener);
        }
    }

    public void setToolbarSelected(int numSelected, int maxItems) {
        if (mToolbarMode == TOOLBAR_MODE.SELECT_FILES && mToolbarCheckBox != null) {
            if (mNumSelected != numSelected) {
                if (numSelected > 0) {
                    // change toolbar's menu to show for selected items
                    mToolbarCheckBox.setVisibility(View.VISIBLE);
                    setToolbarTitle(String.valueOf(numSelected) + " selected");
                } else {
                    mToolbarCheckBox.setVisibility(View.GONE);
                    setToolbarTitle(getString(R.string.app_name));
                }
                mNumSelected = numSelected;
                invalidateOptionsMenu();
            }

            // update the checkbox, but temporarily disable any listener
            // to ensure we don't have a cascading effect
            if (mCheckboxListener != null) {
                mToolbarCheckBox.setOnCheckedChangeListener(null);
            }
            mToolbarCheckBox.setChecked(maxItems > 0 && numSelected == maxItems);
            if (mCheckboxListener != null) {
                mToolbarCheckBox.setOnCheckedChangeListener(mCheckboxListener);
            }
        }
    }

    public int getToolbarSelected() {
        return mNumSelected;
    }

    @NonNull
    public TOOLBAR_MODE getToolbarMode() {
        return mToolbarMode;
    }

    public void setToolbarMode(@NonNull TOOLBAR_MODE mode) {
        if (mToolbarMode != mode && mToolbar != null &&
                mToolbarCheckBox != null && mToolbarButOK != null && mToolbarButCancel != null) {
            if (mode == TOOLBAR_MODE.COPY_FILES || mode == TOOLBAR_MODE.MOVE_FILES) {
                // show paste buttons
                mToolbar.setVisibility(View.VISIBLE);
                mToolbarButOK.setVisibility(View.VISIBLE);
                mToolbarButCancel.setVisibility(View.VISIBLE);
                mToolbarCheckBox.setVisibility(View.GONE);
                setToolbarTitle("");
            } else {
                // show normal select toolbar
                mToolbar.setVisibility(View.VISIBLE);
                mToolbarButOK.setVisibility(View.GONE);
                mToolbarButCancel.setVisibility(View.GONE);
                setToolbarTitle(getString(R.string.app_name));
            }
            mToolbarMode = mode;
        }
    }

    private void setToolbarTitle(@NonNull String text) {
        ActionBar bar = getSupportActionBar();
        if (bar != null && mToolbar != null) {
            if (text.equals("")) {
                bar.setDisplayShowTitleEnabled(false);
                mToolbar.setSubtitle("");
            } else {
                bar.setDisplayShowTitleEnabled(true);
            }
            mToolbar.setTitle(text);
        }
    }

    public enum TOOLBAR_MODE {
        SELECT_FILES,
        COPY_FILES,
        MOVE_FILES
    }

    private static class ScreenSlidePagerAdapter extends FragmentPagerAdapter {
        ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            Fragment f;
            switch (position) {
                case POS_TAB_PLAY:
                default:
                    f = new GameSelectorFragment();
                    break;
                case POS_TAB_EXPLORE:
                    f = new FileSelectorFragment();
                    break;
                case POS_TAB_CREATE:
                    f = new ProjectSelectorFragment();
                    break;
            }
            return f;
        }

        @Override
        public int getCount() {
            return NUM_TABS;
        }

        @NonNull
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case POS_TAB_PLAY:
                default:
                    return "PLAY";
                case POS_TAB_EXPLORE:
                    return "EXPLORE";
                case POS_TAB_CREATE:
                    return "CREATE";
            }
        }
    }
}   