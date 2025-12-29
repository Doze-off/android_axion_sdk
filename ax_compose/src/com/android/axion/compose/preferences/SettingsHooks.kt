/*
 * Copyright (C) 2025 AxionOS Project
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

package com.android.axion.compose.preferences

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberSecureSettingString(
    key: String,
    defaultValue: String = ""
): String {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableStateOf(
            Settings.Secure.getString(contentResolver, key) ?: defaultValue
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getString(contentResolver, key) ?: defaultValue
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    return value
}

@Composable
fun rememberSecureSettingStringState(
    key: String,
    defaultValue: String = ""
): Pair<String, (String) -> Unit> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableStateOf(
            Settings.Secure.getString(contentResolver, key) ?: defaultValue
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getString(contentResolver, key) ?: defaultValue
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    val setter: (String) -> Unit = { newValue ->
        value = newValue
        Settings.Secure.putString(contentResolver, key, newValue)
    }
    
    return value to setter
}

@Composable
fun rememberSecureSettingInt(
    key: String,
    defaultValue: Int = 0
): Int {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableIntStateOf(
            Settings.Secure.getInt(contentResolver, key, defaultValue)
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getInt(contentResolver, key, defaultValue)
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    return value
}

@Composable
fun rememberSecureSettingIntState(
    key: String,
    defaultValue: Int = 0
): Pair<Int, (Int) -> Unit> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableIntStateOf(
            Settings.Secure.getInt(contentResolver, key, defaultValue)
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getInt(contentResolver, key, defaultValue)
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    val setter: (Int) -> Unit = { newValue ->
        value = newValue
        Settings.Secure.putInt(contentResolver, key, newValue)
    }
    
    return value to setter
}

@Composable
fun rememberSecureSettingBoolean(
    key: String,
    defaultValue: Boolean = false
): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableStateOf(
            Settings.Secure.getInt(contentResolver, key, if (defaultValue) 1 else 0) == 1
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getInt(contentResolver, key, if (defaultValue) 1 else 0) == 1
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    return value
}

@Composable
fun rememberSecureSettingBooleanState(
    key: String,
    defaultValue: Boolean = false
): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableStateOf(
            Settings.Secure.getInt(contentResolver, key, if (defaultValue) 1 else 0) == 1
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getInt(contentResolver, key, if (defaultValue) 1 else 0) == 1
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    val setter: (Boolean) -> Unit = { newValue ->
        value = newValue
        Settings.Secure.putInt(contentResolver, key, if (newValue) 1 else 0)
    }
    
    return value to setter
}


@Composable
fun rememberSystemSettingString(
    key: String,
    defaultValue: String = ""
): String {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableStateOf(
            Settings.System.getString(contentResolver, key) ?: defaultValue
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.System.getString(contentResolver, key) ?: defaultValue
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    return value
}

@Composable
fun rememberSystemSettingIntState(
    key: String,
    defaultValue: Int = 0
): Pair<Int, (Int) -> Unit> {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var value by remember(key) {
        mutableIntStateOf(
            Settings.System.getInt(contentResolver, key, defaultValue)
        )
    }
    
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.System.getInt(contentResolver, key, defaultValue)
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(key),
            false,
            observer
        )
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }
    
    val setter: (Int) -> Unit = { newValue ->
        value = newValue
        Settings.System.putInt(contentResolver, key, newValue)
    }
    
    return value to setter
}
