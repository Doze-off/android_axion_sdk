/*
 * Copyright (C) 2025-2026 AxionOS
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class AxPlatformClient {

    private static final String TAG = "AxPlatformClient";
    private static final long RECONNECT_DELAY_MS = 3000L;

    public static final String ACTION_BIND = "com.android.systemui.action.AX_PLATFORM";
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    public static final String KEY_WIFI_SCAN = "wifi_scan";
    public static final String KEY_BATTERY = "battery";
    public static final String KEY_MEDIA = "media";
    public static final String KEY_ALARM = "alarm";
    public static final String KEY_CALENDAR = "calendar";
    public static final String KEY_CONFIG = "config";
    public static final String KEY_DOZE = "doze";
    public static final String KEY_KEYGUARD = "keyguard";
    public static final String KEY_NOW_PLAYING = "now_playing";

    public static final String ACTION_WIFI_CONNECT = "wifi_connect";
    public static final String ACTION_BT_CONNECT = "bt_connect";

    private static volatile AxPlatformClient sInstance;

    private final Object mLock = new Object();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<StateCallback, IAxPlatformCallback> mCallbacks =
            new ConcurrentHashMap<>();

    private volatile IAxPlatformService mService;
    private Context mContext;
    private boolean mBound;

    public interface StateCallback {
        void onStateChanged(@NonNull String key, @NonNull AxFeatureState state);
    }

    @FunctionalInterface
    private interface RemoteAction {
        void run(IAxPlatformService service) throws RemoteException;
    }

    @FunctionalInterface
    private interface RemoteQuery<T> {
        T run(IAxPlatformService service) throws RemoteException;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IAxPlatformService.Stub.asInterface(service);
            Log.d(TAG, "Connected to AxPlatform service");
            reregisterCallbacks();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
            Log.d(TAG, "Disconnected from AxPlatform service");
            scheduleReconnect();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mService = null;
            mBound = false;
            Log.w(TAG, "Binding died, reconnecting");
            scheduleReconnect();
        }
    };

    private AxPlatformClient() {}

    @NonNull
    public static AxPlatformClient getInstance() {
        if (sInstance == null) {
            synchronized (AxPlatformClient.class) {
                if (sInstance == null) {
                    sInstance = new AxPlatformClient();
                }
            }
        }
        return sInstance;
    }

    public void init(@NonNull Context context) {
        synchronized (mLock) {
            if (mContext != null) return;
            mContext = context.getApplicationContext();
        }
        bind();
    }

    public boolean isAvailable() {
        return mService != null;
    }

    public void toggle(@NonNull String feature) {
        call("toggle:" + feature, service -> service.toggle(feature));
    }

    public void setEnabled(@NonNull String feature, boolean enabled) {
        call("setEnabled:" + feature, service -> service.setEnabled(feature, enabled));
    }

    public void setValue(@NonNull String feature, int value) {
        call("setValue:" + feature, service -> service.setValue(feature, value));
    }

    public void performAction(@NonNull String action, @NonNull String param) {
        call("performAction:" + action, service -> service.performAction(action, param));
    }

    @NonNull
    public AxFeatureState getState(@NonNull String key) {
        return AxFeatureState.fromBundle(getStateBundle(key));
    }

    @NonNull
    public Map<String, AxFeatureState> getStates() {
        Bundle states = query("getAllStates", IAxPlatformService::getAllStates, Bundle.EMPTY);
        Map<String, AxFeatureState> result = new HashMap<>();
        for (String key : states.keySet()) {
            result.put(key, AxFeatureState.fromBundle(states.getBundle(key)));
        }
        return result;
    }

    @NonNull
    public String[] getSupportedFeatures() {
        return query("getSupportedFeatures", IAxPlatformService::getSupportedFeatures, new String[0]);
    }

    public void registerCallback(@NonNull Executor executor, @NonNull StateCallback callback) {
        Executor callbackExecutor = Objects.requireNonNull(executor);
        StateCallback stateCallback = Objects.requireNonNull(callback);
        IAxPlatformCallback remote = new IAxPlatformCallback.Stub() {
            @Override
            public void onStateChanged(String key, Bundle state) {
                AxFeatureState featureState = AxFeatureState.fromBundle(state);
                callbackExecutor.execute(() -> stateCallback.onStateChanged(key, featureState));
            }
        };
        if (mCallbacks.putIfAbsent(stateCallback, remote) != null) {
            throw new IllegalArgumentException("Callback is already registered");
        }
        call("registerCallback", service -> service.registerCallback(remote));
    }

    public void unregisterCallback(@NonNull StateCallback callback) {
        IAxPlatformCallback remote = mCallbacks.remove(Objects.requireNonNull(callback));
        if (remote != null) {
            call("unregisterCallback", service -> service.unregisterCallback(remote));
        }
    }

    private Bundle getStateBundle(String key) {
        return query("getState:" + key, service -> service.getState(key), Bundle.EMPTY);
    }

    private void bind() {
        synchronized (mLock) {
            if (mBound || mContext == null) return;
            Intent intent = new Intent(ACTION_BIND).setPackage(SYSTEMUI_PACKAGE);
            try {
                mBound = mContext.bindService(
                        intent,
                        mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                if (!mBound) {
                    Log.w(TAG, "Failed to bind to AxPlatform service");
                    scheduleReconnect();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "bind", e);
            }
        }
    }

    private void scheduleReconnect() {
        mHandler.removeCallbacksAndMessages(this);
        mHandler.postDelayed(this::bind, this, RECONNECT_DELAY_MS);
    }

    private void reregisterCallbacks() {
        for (IAxPlatformCallback callback : mCallbacks.values()) {
            call("reregisterCallback", service -> service.registerCallback(callback));
        }
    }

    private IAxPlatformService getService() {
        return mService;
    }

    private void call(String method, RemoteAction action) {
        try {
            IAxPlatformService service = getService();
            if (service != null) {
                action.run(service);
            } else {
                Log.w(TAG, method + ": service not connected, attempting rebind");
                bind();
            }
        } catch (RemoteException e) {
            Log.e(TAG, method, e);
        }
    }

    private <T> T query(String method, RemoteQuery<T> action, T fallback) {
        try {
            IAxPlatformService service = getService();
            if (service != null) return action.run(service);
        } catch (RemoteException e) {
            Log.e(TAG, method, e);
        }
        return fallback;
    }
}
