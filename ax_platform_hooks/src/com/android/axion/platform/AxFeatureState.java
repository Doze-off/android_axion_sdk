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
import android.os.Bundle;

public final class AxFeatureState {

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_ACTIVE = "active";
    public static final String KEY_AVAILABLE = "available";
    public static final String KEY_STARTING = "starting";
    public static final String KEY_FEATURE = "feature";
    public static final String KEY_TILE_SPEC = "tileSpec";
    public static final String KEY_CATEGORY = "category";
    public static final String KEY_TILE_STATE = "tileState";
    public static final String KEY_LABEL = "label";
    public static final String KEY_SECONDARY_LABEL = "secondaryLabel";
    public static final String KEY_RINGER_MODE = "ringerMode";
    public static final String KEY_HAS_VIBRATOR = "hasVibrator";

    public static final int TILE_STATE_UNAVAILABLE = 0;
    public static final int TILE_STATE_INACTIVE = 1;
    public static final int TILE_STATE_ACTIVE = 2;

    private final Bundle mBundle;

    private AxFeatureState(@Nullable Bundle bundle) {
        mBundle = bundle == null || bundle == Bundle.EMPTY ? Bundle.EMPTY : new Bundle(bundle);
    }

    @NonNull
    public static AxFeatureState empty() {
        return new AxFeatureState(Bundle.EMPTY);
    }

    @NonNull
    public static AxFeatureState fromBundle(@Nullable Bundle bundle) {
        return new AxFeatureState(bundle);
    }

    @NonNull
    public static Builder newBuilder() {
        return new Builder(null);
    }

    @NonNull
    public static Builder newBuilder(@Nullable Bundle bundle) {
        return new Builder(bundle);
    }

    public boolean isEmpty() {
        return mBundle == Bundle.EMPTY || mBundle.isEmpty();
    }

    @NonNull
    public Bundle toBundle() {
        return mBundle == Bundle.EMPTY ? Bundle.EMPTY : new Bundle(mBundle);
    }

    public boolean isEnabled() {
        return mBundle.getBoolean(KEY_ENABLED, false);
    }

    public boolean isActive() {
        return mBundle.getBoolean(KEY_ACTIVE, false);
    }

    public boolean isAvailable() {
        return mBundle.getBoolean(KEY_AVAILABLE, true);
    }

    public boolean isStarting() {
        return mBundle.getBoolean(KEY_STARTING, false);
    }

    public int getTileState() {
        return mBundle.getInt(KEY_TILE_STATE, TILE_STATE_INACTIVE);
    }

    @Nullable
    public String getFeature() {
        return mBundle.getString(KEY_FEATURE);
    }

    @Nullable
    public String getTileSpec() {
        return mBundle.getString(KEY_TILE_SPEC);
    }

    @Nullable
    public String getCategory() {
        return mBundle.getString(KEY_CATEGORY);
    }

    @Nullable
    public String getLabel() {
        return mBundle.getString(KEY_LABEL);
    }

    @Nullable
    public String getSecondaryLabel() {
        return mBundle.getString(KEY_SECONDARY_LABEL);
    }

    public boolean hasRingerMode() {
        return mBundle.containsKey(KEY_RINGER_MODE);
    }

    public int getRingerMode(int fallback) {
        return mBundle.getInt(KEY_RINGER_MODE, fallback);
    }

    public boolean hasVibrator() {
        return mBundle.getBoolean(KEY_HAS_VIBRATOR, true);
    }

    public boolean containsKey(@NonNull String key) {
        return mBundle.containsKey(key);
    }

    public boolean getBoolean(@NonNull String key, boolean fallback) {
        return mBundle.getBoolean(key, fallback);
    }

    public int getInt(@NonNull String key, int fallback) {
        return mBundle.getInt(key, fallback);
    }

    public float getFloat(@NonNull String key, float fallback) {
        return mBundle.getFloat(key, fallback);
    }

    public long getLong(@NonNull String key, long fallback) {
        return mBundle.getLong(key, fallback);
    }

    @Nullable
    public String getString(@NonNull String key) {
        return mBundle.getString(key);
    }

    @NonNull
    public String getString(@NonNull String key, @NonNull String fallback) {
        return mBundle.getString(key, fallback);
    }

    public static final class Builder {
        private final Bundle mBundle;

        private Builder(@Nullable Bundle bundle) {
            mBundle = bundle == null || bundle == Bundle.EMPTY ? new Bundle() : new Bundle(bundle);
        }

        @NonNull
        public Builder setEnabled(boolean enabled) {
            mBundle.putBoolean(KEY_ENABLED, enabled);
            return this;
        }

        @NonNull
        public Builder setActive(boolean active) {
            mBundle.putBoolean(KEY_ACTIVE, active);
            return this;
        }

        @NonNull
        public Builder setAvailable(boolean available) {
            mBundle.putBoolean(KEY_AVAILABLE, available);
            return this;
        }

        @NonNull
        public Builder setStarting(boolean starting) {
            mBundle.putBoolean(KEY_STARTING, starting);
            return this;
        }

        @NonNull
        public Builder setTileState(int tileState) {
            mBundle.putInt(KEY_TILE_STATE, tileState);
            return this;
        }

        @NonNull
        public Builder setFeature(@Nullable String feature) {
            if (feature == null) {
                mBundle.remove(KEY_FEATURE);
            } else {
                mBundle.putString(KEY_FEATURE, feature);
            }
            return this;
        }

        @NonNull
        public Builder setTileSpec(@Nullable String tileSpec) {
            if (tileSpec == null) {
                mBundle.remove(KEY_TILE_SPEC);
            } else {
                mBundle.putString(KEY_TILE_SPEC, tileSpec);
            }
            return this;
        }

        @NonNull
        public Builder setCategory(@Nullable String category) {
            if (category == null) {
                mBundle.remove(KEY_CATEGORY);
            } else {
                mBundle.putString(KEY_CATEGORY, category);
            }
            return this;
        }

        @NonNull
        public Builder setLabel(@Nullable String label) {
            if (label == null) {
                mBundle.remove(KEY_LABEL);
            } else {
                mBundle.putString(KEY_LABEL, label);
            }
            return this;
        }

        @NonNull
        public Builder setSecondaryLabel(@Nullable String label) {
            if (label == null) {
                mBundle.remove(KEY_SECONDARY_LABEL);
            } else {
                mBundle.putString(KEY_SECONDARY_LABEL, label);
            }
            return this;
        }

        @NonNull
        public Builder setRingerMode(int mode) {
            mBundle.putInt(KEY_RINGER_MODE, mode);
            return this;
        }

        @NonNull
        public Builder setHasVibrator(boolean hasVibrator) {
            mBundle.putBoolean(KEY_HAS_VIBRATOR, hasVibrator);
            return this;
        }

        @NonNull
        public Builder putBoolean(@NonNull String key, boolean value) {
            mBundle.putBoolean(key, value);
            return this;
        }

        @NonNull
        public Builder putInt(@NonNull String key, int value) {
            mBundle.putInt(key, value);
            return this;
        }

        @NonNull
        public Builder putString(@NonNull String key, @Nullable String value) {
            mBundle.putString(key, value);
            return this;
        }

        @NonNull
        public Builder putIntArray(@NonNull String key, @Nullable int[] value) {
            mBundle.putIntArray(key, value);
            return this;
        }

        @NonNull
        public AxFeatureState build() {
            return new AxFeatureState(mBundle);
        }
    }
}
