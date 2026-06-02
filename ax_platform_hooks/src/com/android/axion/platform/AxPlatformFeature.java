/*
 * Copyright (C) 2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.platform;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.service.quicksettings.TileService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AxPlatformFeature {

    public static final String WIFI = "wifi";
    public static final String MOBILE_DATA = "mobile_data";
    public static final String BLUETOOTH = "bluetooth";
    public static final String HOTSPOT = "hotspot";
    public static final String FLASHLIGHT = "flashlight";
    public static final String LOCATION = "location";
    public static final String ROTATION = "rotation";
    public static final String BATTERY_SAVER = "battery_saver";
    public static final String ZEN = "zen";
    public static final String AOD = "aod";
    public static final String AMBIENT_DISPLAY = "ambient_display";
    public static final String DATA_SAVER = "data_saver";
    public static final String AIRPLANE_MODE = "airplane_mode";
    public static final String NFC = "nfc";
    public static final String DARK_MODE = "dark_mode";
    public static final String NIGHT_LIGHT = "night_light";
    public static final String COLOR_INVERSION = "color_inversion";
    public static final String COLOR_CORRECTION = "color_correction";
    public static final String REDUCE_BRIGHTNESS = "reduce_brightness";
    public static final String ONE_HANDED_MODE = "one_handed_mode";
    public static final String HEADS_UP = "heads_up";
    public static final String AUTO_SYNC = "auto_sync";
    public static final String CAMERA_PRIVACY = "camera_privacy";
    public static final String MIC_PRIVACY = "mic_privacy";
    public static final String WORK_PROFILE = "work_profile";
    public static final String USB_TETHER = "usb_tether";
    public static final String DREAM = "dream";
    public static final String READING_MODE = "reading_mode";
    public static final String POWER_SHARE = "power_share";
    public static final String CAFFEINE = "caffeine";
    public static final String VPN = "vpn";
    public static final String CAST = "cast";
    public static final String PROFILES = "profiles";
    public static final String SMART_PIXELS = "smart_pixels";
    public static final String SCREEN_RECORD = "screen_record";
    public static final String SCREENSHOT = "screenshot";
    public static final String RINGER_MODE = "ringer_mode";
    public static final String ALARM = "alarm";
    public static final String CONTROLS = "controls";
    public static final String WALLET = "wallet";
    public static final String QR_CODE_SCANNER = "qr_code_scanner";
    public static final String FONT_SCALING = "font_scaling";
    public static final String RECORD_ISSUE = "record_issue";
    public static final String HEARING_DEVICES = "hearing_devices";
    public static final String NOTES = "notes";
    public static final String DESKTOP_EFFECTS = "desktopeffects";
    public static final String VOLUME = "volume";
    public static final String FIVE_G = "five_g";
    public static final String ROUTINES = "routines";
    public static final String DNS = "dns";

    public static final String CATEGORY_CONNECTIVITY = TileService.CATEGORY_CONNECTIVITY;
    public static final String CATEGORY_UTILITIES = TileService.CATEGORY_UTILITIES;
    public static final String CATEGORY_DISPLAY = TileService.CATEGORY_DISPLAY;
    public static final String CATEGORY_PRIVACY = TileService.CATEGORY_PRIVACY;
    public static final String CATEGORY_ACCESSIBILITY = TileService.CATEGORY_ACCESSIBILITY;

    private static final Entry[] ENTRIES = new Entry[] {
            entry(WIFI, CATEGORY_CONNECTIVITY, "internet", "wifi"),
            entry(MOBILE_DATA, CATEGORY_CONNECTIVITY, "cell", "mobiledata", "mobile_data"),
            entry(BLUETOOTH, CATEGORY_CONNECTIVITY, "bt", "bluetooth"),
            entry(HOTSPOT, CATEGORY_CONNECTIVITY, "hotspot"),
            entry(FLASHLIGHT, CATEGORY_UTILITIES, "flashlight"),
            entry(LOCATION, CATEGORY_PRIVACY, "location"),
            entry(ROTATION, CATEGORY_DISPLAY, "rotation"),
            entry(BATTERY_SAVER, CATEGORY_UTILITIES, "battery", "saver", "battery_saver"),
            entry(ZEN, CATEGORY_UTILITIES, "dnd", "modes_dnd", "zen"),
            entry(AOD, CATEGORY_DISPLAY, "aod"),
            entry(AMBIENT_DISPLAY, CATEGORY_DISPLAY, "ambient_display"),
            entry(DATA_SAVER, CATEGORY_CONNECTIVITY, "saver", "data_saver"),
            entry(AIRPLANE_MODE, CATEGORY_CONNECTIVITY, "airplane", "airplane_mode"),
            entry(NFC, CATEGORY_CONNECTIVITY, "nfc"),
            entry(DARK_MODE, CATEGORY_DISPLAY, "dark", "dark_mode", "ui_mode_night"),
            entry(NIGHT_LIGHT, CATEGORY_DISPLAY, "night", "night_light"),
            entry(COLOR_INVERSION, CATEGORY_ACCESSIBILITY, "inversion", "color_inversion"),
            entry(COLOR_CORRECTION, CATEGORY_ACCESSIBILITY, "color_correction"),
            entry(REDUCE_BRIGHTNESS, CATEGORY_ACCESSIBILITY, "reduce_brightness", "extra_dim"),
            entry(ONE_HANDED_MODE, CATEGORY_ACCESSIBILITY, "onehanded", "one_handed_mode"),
            entry(HEADS_UP, CATEGORY_ACCESSIBILITY, "heads_up"),
            entry(AUTO_SYNC, CATEGORY_CONNECTIVITY, "sync", "auto_sync"),
            entry(CAMERA_PRIVACY, CATEGORY_PRIVACY, "cameratoggle", "camera", "camera_privacy"),
            entry(MIC_PRIVACY, CATEGORY_PRIVACY, "mictoggle", "mic", "mic_privacy"),
            entry(WORK_PROFILE, CATEGORY_UTILITIES, "work", "work_profile"),
            entry(USB_TETHER, CATEGORY_CONNECTIVITY, "usb_tether"),
            entry(DREAM, CATEGORY_DISPLAY, "dream", "screensaver"),
            entry(READING_MODE, CATEGORY_DISPLAY, "reading_mode"),
            entry(POWER_SHARE, CATEGORY_UTILITIES, "powershare", "power_share", "reverse"),
            entry(CAFFEINE, CATEGORY_DISPLAY, "caffeine"),
            entry(VPN, CATEGORY_CONNECTIVITY, "vpn"),
            entry(CAST, CATEGORY_CONNECTIVITY, "cast"),
            entry(PROFILES, CATEGORY_UTILITIES, "profiles"),
            entry(SMART_PIXELS, CATEGORY_DISPLAY, "smart_pixels"),
            entry(SCREEN_RECORD, CATEGORY_UTILITIES, "screenrecord", "screen_record"),
            entry(SCREENSHOT, CATEGORY_UTILITIES, "screenshot"),
            entry(RINGER_MODE, CATEGORY_UTILITIES, "sound", "ringer", "ringer_mode", "sound_mode"),
            entry(ALARM, CATEGORY_UTILITIES, "alarm"),
            entry(CONTROLS, CATEGORY_UTILITIES, "controls"),
            entry(WALLET, CATEGORY_UTILITIES, "wallet"),
            entry(QR_CODE_SCANNER, CATEGORY_UTILITIES, "qr_code_scanner"),
            entry(FONT_SCALING, CATEGORY_ACCESSIBILITY, "font_scaling"),
            entry(RECORD_ISSUE, CATEGORY_UTILITIES, "record_issue"),
            entry(HEARING_DEVICES, CATEGORY_ACCESSIBILITY, "hearing_devices"),
            entry(NOTES, CATEGORY_UTILITIES, "notes"),
            entry(DESKTOP_EFFECTS, CATEGORY_UTILITIES, "desktopeffects"),
            entry(VOLUME, CATEGORY_UTILITIES, "volume"),
            entry(FIVE_G, CATEGORY_CONNECTIVITY, "five_g"),
            entry(ROUTINES, CATEGORY_UTILITIES, "routines"),
            entry(DNS, CATEGORY_CONNECTIVITY, "dns"),
    };

    private static final String[] BASE_FEATURES = new String[] {
            WIFI,
            MOBILE_DATA,
            BLUETOOTH,
            HOTSPOT,
            FLASHLIGHT,
            LOCATION,
            ROTATION,
            BATTERY_SAVER,
            ZEN,
            AOD,
            AMBIENT_DISPLAY,
            DATA_SAVER,
            AIRPLANE_MODE,
            DARK_MODE,
            NIGHT_LIGHT,
            COLOR_INVERSION,
            COLOR_CORRECTION,
            REDUCE_BRIGHTNESS,
            ONE_HANDED_MODE,
            HEADS_UP,
            AUTO_SYNC,
    };

    private static final Map<String, Entry> FEATURE_TO_ENTRY = new LinkedHashMap<>();
    private static final Map<String, Entry> SPEC_TO_ENTRY = new LinkedHashMap<>();

    static {
        for (Entry entry : ENTRIES) {
            FEATURE_TO_ENTRY.put(entry.feature, entry);
            for (String spec : entry.tileSpecs) {
                SPEC_TO_ENTRY.put(spec, entry);
            }
        }
    }

    private AxPlatformFeature() {}

    @Nullable
    public static String resolve(@NonNull String spec) {
        Entry entry = SPEC_TO_ENTRY.get(normalize(spec));
        return entry != null ? entry.feature : null;
    }

    @Nullable
    public static String getCategory(@NonNull String feature) {
        Entry entry = FEATURE_TO_ENTRY.get(feature);
        return entry != null ? entry.category : null;
    }

    @Nullable
    public static String getPrimaryTileSpec(@NonNull String feature) {
        Entry entry = FEATURE_TO_ENTRY.get(feature);
        return entry != null ? entry.tileSpecs[0] : null;
    }

    @NonNull
    public static String[] getTileSpecs(@NonNull String feature) {
        Entry entry = FEATURE_TO_ENTRY.get(feature);
        return entry != null ? entry.tileSpecs.clone() : new String[0];
    }

    @NonNull
    public static String[] getFeaturesForCategory(@NonNull String category) {
        List<String> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (category.equals(entry.category)) {
                result.add(entry.feature);
            }
        }
        return result.toArray(new String[0]);
    }

    @NonNull
    public static String[] getBaseFeatures() {
        return BASE_FEATURES.clone();
    }

    @NonNull
    public static String[] getKnownFeatures() {
        return FEATURE_TO_ENTRY.keySet().toArray(new String[0]);
    }

    public static boolean isKnownFeature(@NonNull String feature) {
        return FEATURE_TO_ENTRY.containsKey(feature);
    }

    private static Entry entry(String feature, String category, String... tileSpecs) {
        return new Entry(feature, category, tileSpecs);
    }

    private static String normalize(String spec) {
        return spec.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        final String feature;
        final String category;
        final String[] tileSpecs;

        Entry(String feature, String category, String[] tileSpecs) {
            this.feature = feature;
            this.category = category;
            this.tileSpecs = tileSpecs;
        }
    }
}
