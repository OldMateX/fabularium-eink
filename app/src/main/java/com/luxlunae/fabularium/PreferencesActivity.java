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

import androidx.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;

public class PreferencesActivity extends Activity {
    public static final String KEY_PREF_BYTE_ORDER = "pref_byteOrder";
    public static final String KEY_PREF_DEFAULT_CHARSET = "pref_defaultCharset";
    public static final String KEY_PREF_CURSOR_WIDTH = "pref_cursorWidth";
    public static final String KEY_PREF_VSCROLLBAR_WIDTH = "pref_vscrollbarWidth";
    public static final String KEY_PREF_VSCROLLBAR_START_COLOR = "pref_vscrollbarStartColor";
    public static final String KEY_PREF_VSCROLLBAR_END_COLOR = "pref_vscrollbarEndColor";
    public static final String KEY_PREF_VSCROLLBAR_GRAD_ORIENT = "pref_vscrollbarGradOrient";
    public static final String KEY_PREF_HSCROLLBAR_HEIGHT = "pref_hscrollbarHeight";
    public static final String KEY_PREF_HSCROLLBAR_START_COLOR = "pref_hscrollbarStartColor";
    public static final String KEY_PREF_HSCROLLBAR_END_COLOR = "pref_hscrollbarEndColor";
    public static final String KEY_PREF_HSCROLLBAR_GRAD_ORIENT = "pref_hscrollbarGradOrient";
    public static final String KEY_PREF_BORDER_STYLE = "pref_borderStyle";
    public static final String KEY_PREF_BORDER_COLOR = "pref_borderColor";
    public static final String KEY_PREF_BORDER_HEIGHT = "pref_borderHeight";
    public static final String KEY_PREF_BORDER_WIDTH = "pref_borderWidth";
    public static final String KEY_PREF_SERVER_URL = "pref_serverURL";
    public static final String KEY_PREF_BUILTIN_KEYBOARD = "pref_useBuiltinKeyboard";
    public static final String KEY_PREF_HTML_ENABLE_JS = "pref_htmlEnableJavascript";
    public static final String KEY_PREF_HTML_ENABLE_DOM = "pref_htmlEnableDomStorageAPI";
    public static final String KEY_PREF_HTML_ENABLE_ZOOM = "pref_htmlEnableZoomControls";
    public static final String KEY_PREF_HTML_DISPLAY_ZOOM = "pref_htmlDisplayZoomControls";
    public static final String KEY_PREF_HTML_LOAD_OVEVIEW = "pref_htmlLoadWithOverview";
    public static final String KEY_PREF_HTML_USE_WIDE_VIEWPORT = "pref_htmlUseWideViewport";
    public static final String KEY_PREF_DEFAULT_FONT_SIZE_TEXT_BUF = "pref_defaultFontSizeBUF";
    public static final String KEY_PREF_DEFAULT_FONT_SIZE_TEXT_GRID = "pref_defaultFontSizeGRID";
    public static final String KEY_PREF_DEFAULT_AUTO_RESIZE_LARGE_IMAGES = "pref_autoResizeLargeImages";
    public static final String KEY_PREF_DEFAULT_LEADING_TEXT_BUF = "pref_defaultLeadingBUF";
    public static final String KEY_PREF_DEFAULT_LEADING_TEXT_GRID = "pref_defaultLeadingGRID";
    public static final String KEY_PREF_ENABLE_GRAPHICS = "pref_enableGraphics";
    public static final String KEY_PREF_ENABLE_SOUNDS = "pref_enableSounds";
    public static final String KEY_PREF_ENABLE_HYPERLINKS = "pref_enableHyperlinks";
    public static final String KEY_PREF_ENABLE_DATETIME = "pref_enableDateTime";
    public static final String KEY_PREF_ENABLE_TIMER = "pref_enableTimer";
    public static final String KEY_PREF_SHOW_RAW_HTML = "pref_showRawHTML";
    public static final String KEY_PREF_ENABLE_TTS = "pref_enableTTS";
    public static final String KEY_PREF_TTS_RATE = "pref_ttsRate";
    public static final String KEY_PREF_TTS_PITCH = "pref_ttsPitch";
    public static final String KEY_PREF_LOCALE = "pref_locale";
    public static final String KEY_PREF_DEFAULT_LMARGIN_WIN = "pref_defaultLMarginWIN";
    public static final String KEY_PREF_DEFAULT_RMARGIN_WIN = "pref_defaultRMarginWIN";
    public static final String KEY_PREF_DEFAULT_TMARGIN_WIN = "pref_defaultTMarginWIN";
    public static final String KEY_PREF_DEFAULT_BMARGIN_WIN = "pref_defaultBMarginWIN";
    public static final String KEY_PREF_WIN_BGCOLOR = "pref_winBGColor";
    public static final String KEY_PREF_DEFAULT_LMARGIN_BUF = "pref_defaultLMarginBUF";
    public static final String KEY_PREF_DEFAULT_RMARGIN_BUF = "pref_defaultRMarginBUF";
    public static final String KEY_PREF_DEFAULT_TMARGIN_BUF = "pref_defaultTMarginBUF";
    public static final String KEY_PREF_DEFAULT_BMARGIN_BUF = "pref_defaultBMarginBUF";
    public static final String KEY_PREF_DEFAULT_LMARGIN_GRID = "pref_defaultLMarginGRID";
    public static final String KEY_PREF_DEFAULT_RMARGIN_GRID = "pref_defaultRMarginGRID";
    public static final String KEY_PREF_DEFAULT_TMARGIN_GRID = "pref_defaultTMarginGRID";
    public static final String KEY_PREF_DEFAULT_BMARGIN_GRID = "pref_defaultBMarginGRID";
    public static final String KEY_PREF_SYNC_SCREEN_BACKGROUND = "pref_syncScreenBackground";
    public static final String KEY_PREF_TEXTBUF_WIDTH_MULTIPLIER = "pref_textBufWidthMultiplier";
    public static final String KEY_PREF_TEXTBUF_HEIGHT_MULTIPLIER = "pref_textBufHeightMultiplier";
    public static final String KEY_PREF_TEXTGRID_WIDTH_MULTIPLIER = "pref_textGridWidthMultiplier";
    public static final String KEY_PREF_TEXTGRID_HEIGHT_MULTIPLIER = "pref_textGridHeightMultiplier";
    public static final String KEY_PREF_GRAPHICS_WIDTH_MULTIPLIER = "pref_graphicsWidthMultiplier";
    public static final String KEY_PREF_GRAPHICS_HEIGHT_MULTIPLIER = "pref_graphicsHeightMultiplier";
    public static final String KEY_PREF_TEXT_FILE_EXTS = "pref_fileMgmtTextFileExts";
    public static final String KEY_PREF_COPY_GAME_FILE_ON_INSTALL = "pref_fileMgmtCopyGameFileOnInstall";
    public static final String KEY_PREF_APP_PATH = "pref_fileMgmtAppPath";
    public static final String KEY_PREF_GAME_PATH = "pref_fileMgmtGamePath";
    public static final String KEY_PREF_GAME_DATA_PATH = "pref_fileMgmtGameDataPath";
    public static final String KEY_PREF_PROJECTS_PATH = "pref_fileMgmtProjectsPath";
    public static final String KEY_PREF_LIB_PATH = "pref_fileMgmtLibPath";
    public static final String KEY_PREF_INCLUDE_PATH = "pref_fileMgmtIncludePath";
    public static final String KEY_PREF_FONTS_PATH = "pref_fileMgmtFontsPath";

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }   

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean eink = prefs.getBoolean("pref_eink_mode", true);   
        setTheme(eink ? R.style.Theme_Fabularium_EInk : R.style.Theme_Fabularium);

        super.onCreate(savedInstanceState);

        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            getWindow().getDecorView().setBackgroundColor(tv.data);
        } else if (tv.resourceId != 0) {
            getWindow().getDecorView().setBackgroundResource(tv.resourceId);
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static ParcelableSharedPrefs getReadOnlyPrefs(Context c) {
        return new ParcelableSharedPrefs(PreferenceManager.getDefaultSharedPreferences(c));
    }

    public static class ParcelableSharedPrefs implements Parcelable {
        public static final Creator<ParcelableSharedPrefs> CREATOR = new Creator<ParcelableSharedPrefs>() {
            @Override
            public ParcelableSharedPrefs createFromParcel(Parcel in) {
                return new ParcelableSharedPrefs(in);
            }

            @Override
            public ParcelableSharedPrefs[] newArray(int size) {
                return new ParcelableSharedPrefs[size];
            }
        };

        @Nullable
        private Bundle mPrefs;

        ParcelableSharedPrefs(SharedPreferences pref) {
            String key;
            Object val;
            try {
                Map<String, ?> prefs = pref.getAll();
                mPrefs = new Bundle();
                for (Map.Entry<String, ?> entry : prefs.entrySet()) {
                    key = entry.getKey();
                    val = entry.getValue();
                    if (val != null) {
                        if (val instanceof String) {
                            mPrefs.putString(key, (String) val);
                        } else if (val instanceof Integer) {
                            mPrefs.putInt(key, (int) val);
                        } else if (val instanceof Boolean) {
                            mPrefs.putBoolean(key, (boolean) val);
                        }
                    }
                }
            } catch (NullPointerException e) {
                mPrefs = null;
            }
        }

        @SuppressLint("ParcelClassLoader")
        ParcelableSharedPrefs(Parcel in) {
            mPrefs = in.readBundle();
        }

        public boolean getBoolean(String key, boolean defValue) {
            if (mPrefs == null) return defValue;
            if (mPrefs.containsKey(key)) return mPrefs.getBoolean(key);
            return defValue;
        }

        public String getString(String key, String defValue) {
            if (mPrefs == null) return defValue;
            if (mPrefs.containsKey(key)) return mPrefs.getString(key);
            return defValue;
        }

        @Override
        public int describeContents() { return 0; }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            if (mPrefs != null) out.writeBundle(mPrefs);
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference pref = screen.getPreference(i);
                if (pref instanceof PreferenceCategory) {
                    ((PreferenceCategory) pref).setLayoutResource(R.layout.pref_category_eink);
                } else if (pref instanceof PreferenceScreen) {
                    PreferenceScreen subScreen = (PreferenceScreen) pref;
                    for (int j = 0; j < subScreen.getPreferenceCount(); j++) {
                        Preference subPref = subScreen.getPreference(j);
                        if (subPref instanceof PreferenceCategory) {
                            ((PreferenceCategory) subPref).setLayoutResource(R.layout.pref_category_eink);
                        }
                    }
                }
            }

            findPreference("pref_dark_mode").setOnPreferenceChangeListener((pref, newValue) -> {
                AppCompatDelegate.setDefaultNightMode(
                    (Boolean) newValue ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                getActivity().recreate();
                return true;
            });

            findPreference("pref_eink_mode").setOnPreferenceChangeListener((pref, newValue) -> {
                getActivity().recreate();
                return true;
            });
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            if (view != null) {
                TypedValue tv = new TypedValue();
                getActivity().getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true);
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    view.setBackgroundColor(tv.data);
                } else if (tv.resourceId != 0) {
                    view.setBackgroundResource(tv.resourceId);
                }
            }
            return view;
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); ++i) {
                Preference preference = getPreferenceScreen().getPreference(i);
                if (preference instanceof PreferenceGroup) {
                    PreferenceGroup preferenceGroup = (PreferenceGroup) preference;
                    for (int j = 0; j < preferenceGroup.getPreferenceCount(); ++j) {
                        Preference singlePref = preferenceGroup.getPreference(j);
                        updatePreference(singlePref, singlePref.getKey());
                    }
                } else {
                    if (preference instanceof CheckBoxPreference) {
                        continue;
                    }
                    updatePreference(preference, preference.getKey());
                }
            }
            paintListBackgrounds(getView());
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePreference(findPreference(key), key);
        }

        private void updatePreference(@Nullable Preference preference, String key) {
            if (preference == null || preference instanceof CheckBoxPreference || preference instanceof SwitchPreference) {
                return;
            }
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                listPreference.setSummary(listPreference.getEntry());
                return;
            }
            SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();
            preference.setSummary(sharedPrefs.getString(key, "Default"));
        }

        /**
         * Called when the user taps a nested PreferenceScreen (e.g. "Windows",
         * "Typography"). The framework opens a new Dialog window for the sub-screen.
         * We post a delayed task to walk that dialog's view tree and force the
         * ListView background, because the Boox Flow's e-ink driver ignores
         * theme backgrounds for dialog-internal ListViews.
         */
        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            super.onPreferenceTreeClick(preferenceScreen, preference);

            if (preference instanceof PreferenceScreen) {
                // The nested screen opens in its OWN dialog window,
                // NOT inside the activity's window. We must walk
                // the dialog's decor view, not the activity's.
                final android.app.Dialog dialog =
                    ((PreferenceScreen) preference).getDialog();
                if (dialog != null) {
                    getView().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (dialog.getWindow() != null) {
                                paintListBackgrounds(
                                    dialog.getWindow().getDecorView());
                            }
                        }
                    }, 300);
                }
            }
            return true;
        }   
        
        /**
         * Recursively walks a view tree and forces every ListView to have
         * the theme's colorBackground. Brute-force fix for e-ink devices
         * where the platform ignores theme attributes on dialog ListViews.
         */
        /**
         * Recursively walks a view tree and forces backgrounds AND text
         * colours on every relevant view. Brute-force fix for e-ink
         * devices (especially Boox) where the platform ignores theme
         * attributes on dialog ListViews and TextViews.
         */
        private void paintListBackgrounds(View view) {
            if (view == null) return;

            // Determine correct colours based on current night mode
            boolean isNight = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;   

            int bgColor = isNight ? 0xFF000000 : 0xFFFFFFFF;
            int textColor = isNight ? 0xFFFFFFFF : 0xFF000000;
            int secondaryTextColor = isNight ? 0xFFCCCCCC : 0xFF333333;

            if (view instanceof android.widget.ListView) {
                view.setBackgroundColor(bgColor);
            }
            if (view instanceof android.widget.FrameLayout) {
                view.setBackgroundColor(bgColor);
            }
            if (view instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) view;
                // Force readable text on any TextView in the dialog
                int currentColor = tv.getCurrentTextColor();
                // If text is the same as background (invisible), fix it
                if (currentColor == bgColor) {
                    tv.setTextColor(textColor);
                }
            }
            if (view instanceof android.widget.EditText) {
                android.widget.EditText et = (android.widget.EditText) view;
                int currentColor = et.getCurrentTextColor();
                if (currentColor == bgColor) {
                    et.setTextColor(textColor);
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    paintListBackgrounds(group.getChildAt(i));
                }
            }
        }   
    }   
}   