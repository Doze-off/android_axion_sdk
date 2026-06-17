/*
 * Copyright 2025-2026 AxionOS
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
package com.android.axion.preference

import android.R as AndroidR
import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.R as PreferenceR
import com.android.axion.compose.host.AxComposeView
import com.android.axion.compose.preferences.ClickablePreference as AxClickablePreference
import com.android.axion.compose.preferences.ListPreference as AxComposeListPreference
import com.android.axion.compose.preferences.PreferencePosition
import com.android.axion.compose.preferences.SliderPreference as AxComposeSliderPreference
import com.android.axion.compose.preferences.SwitchPreference as AxComposeSwitchPreference
import com.android.axion.compose.theme.AxionTheme
import com.android.settingslib.widget.GroupSectionDividerMixin
import kotlin.math.roundToInt

open class AxPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = PreferenceR.attr.preferenceStyle,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
    init {
        layoutResource = R.layout.ax_preference_compose
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.bindAxPreference {
            AxClickablePreference(
                title = titleText(),
                summary = summaryText(),
                enabled = isEnabled,
                position = axPosition(),
                onClick = { performClick() },
            )
        }
    }
}

class AxSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = PreferenceR.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0,
) : SwitchPreferenceCompat(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
    init {
        layoutResource = R.layout.ax_preference_compose
        widgetLayoutResource = 0
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.bindAxPreference {
            AxComposeSwitchPreference(
                title = titleText(),
                summary = summaryText(),
                checked = isChecked,
                enabled = isEnabled,
                position = axPosition(),
                onCheckedChange = { checked ->
                    if (callChangeListener(checked)) {
                        isChecked = checked
                    }
                },
            )
        }
    }
}

class AxListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = PreferenceR.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0,
) : ListPreference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
    init {
        layoutResource = R.layout.ax_preference_compose
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.bindAxPreference {
            val options = options()
            if (options.isEmpty()) {
                AxClickablePreference(
                    title = titleText(),
                    summary = summaryText(),
                    enabled = isEnabled,
                    position = axPosition(),
                    onClick = { performClick() },
                )
            } else {
                AxComposeListPreference(
                    title = titleText(),
                    summary = summaryText(),
                    options = options,
                    value = value ?: "",
                    enabled = isEnabled,
                    position = axPosition(),
                    onValueChange = { newValue ->
                        if (callChangeListener(newValue)) {
                            value = newValue
                        }
                    },
                )
            }
        }
    }
}

class AxSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = PreferenceR.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0,
) : SeekBarPreference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
    private val valueSuffix: String
    private val resetValue: Int

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AxSeekBarPreference)
        valueSuffix = typedArray.getString(R.styleable.AxSeekBarPreference_valueSuffix).orEmpty()
        typedArray.recycle()
        val defaultTypedArray = context.obtainStyledAttributes(
            attrs,
            intArrayOf(AndroidR.attr.defaultValue),
        )
        resetValue = defaultTypedArray.getInt(0, min).coerceIn(min, max)
        defaultTypedArray.recycle()
        layoutResource = R.layout.ax_preference_compose
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        holder.bindAxPreference {
            var sliderValue by remember(key, value) {
                mutableFloatStateOf(snapValue(value.toFloat()).toFloat())
            }
            AxComposeSliderPreference(
                title = titleText(),
                summary = summaryText().orEmpty(),
                value = sliderValue,
                valueRange = min.toFloat()..max.toFloat(),
                displayValue = "${sliderValue.roundToInt()}$valueSuffix",
                enabled = isEnabled,
                position = axPosition(),
                onValueChange = { newValue ->
                    sliderValue = snapValue(newValue).toFloat()
                },
                onValueChangeFinished = {
                    val newValue = sliderValue.roundToInt()
                    if (callChangeListener(newValue)) {
                        value = newValue
                    } else {
                        sliderValue = value.toFloat()
                    }
                },
                onReset = {
                    val newValue = snapValue(resetValue.toFloat())
                    if (callChangeListener(newValue)) {
                        sliderValue = newValue.toFloat()
                        value = newValue
                    } else {
                        sliderValue = snapValue(value.toFloat()).toFloat()
                    }
                },
            )
        }
    }

    private fun snapValue(newValue: Float): Int {
        val increment = getSeekBarIncrement().takeIf { it > 0 } ?: 1
        return (min + ((newValue - min) / increment).roundToInt() * increment).coerceIn(min, max)
    }
}

private fun PreferenceViewHolder.bindAxPreference(content: @Composable () -> Unit) {
    itemView.setPadding(0, 0, 0, 0)
    itemView.minimumHeight = 0
    val composeView = itemView as? AxComposeView
        ?: itemView.findViewById<AxComposeView>(R.id.ax_preference_compose_view)
        ?: return
    composeView.setContent {
        AxionTheme(applySystemBars = false, content = content)
    }
}

private fun Preference.titleText(): String = title?.toString().orEmpty()

private fun Preference.summaryText(): String? = summary?.toString()?.takeIf { it.isNotEmpty() }

private fun AxListPreference.options(): List<Pair<String, String>> {
    val labels = entries ?: return emptyList()
    val values = entryValues ?: return emptyList()
    return values.zip(labels).map { (value, label) -> value.toString() to label.toString() }
}

private fun Preference.axPosition(): PreferencePosition {
    val parentGroup = parent ?: return PreferencePosition.Single
    val count = parentGroup.axPreferenceCount()
    if (count <= 1) {
        return PreferencePosition.Single
    }
    val index = parentGroup.axPreferenceIndex(this)
    if (index < 0) {
        return PreferencePosition.Single
    }
    return when (index) {
        0 -> PreferencePosition.Top
        count - 1 -> PreferencePosition.Bottom
        else -> PreferencePosition.Middle
    }
}

private fun PreferenceGroup.axPreferenceCount(): Int {
    var count = 0
    for (i in 0 until preferenceCount) {
        if (getPreference(i).isAxPreference()) {
            count++
        }
    }
    return count
}

private fun PreferenceGroup.axPreferenceIndex(preference: Preference): Int {
    var index = 0
    for (i in 0 until preferenceCount) {
        val child = getPreference(i)
        if (!child.isAxPreference()) {
            continue
        }
        if (child === preference) {
            return index
        }
        index++
    }
    return -1
}

private fun Preference.isAxPreference(): Boolean {
    return this is AxPreference
            || this is AxSwitchPreference
            || this is AxListPreference
            || this is AxSeekBarPreference
}
