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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

interface PreferenceGroupScope {
    fun item(content: @Composable () -> Unit)
}

class PreferenceGroupScopeImpl : PreferenceGroupScope {
    private val items = mutableListOf<@Composable () -> Unit>()

    override fun item(content: @Composable () -> Unit) {
        items.add(content)
    }

    fun getItems(): List<@Composable () -> Unit> = items
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    spaceBetween: Dp = 1.dp,
    content: PreferenceGroupScope.() -> Unit
) {
    val scope = PreferenceGroupScopeImpl()
    scope.content()
    val items = scope.getItems()

    Column(modifier = modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 8.dp, top = 4.dp)
            )
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(spaceBetween)
        ) {
            items.forEachIndexed { index, composable ->
                val position = when {
                    items.size == 1 -> PreferencePosition.Single
                    index == 0 -> PreferencePosition.Top
                    index == items.size - 1 -> PreferencePosition.Bottom
                    else -> PreferencePosition.Middle
                }
                CompositionLocalProvider(LocalPreferencePosition provides position) {
                    composable()
                }
            }
        }
    }
}
