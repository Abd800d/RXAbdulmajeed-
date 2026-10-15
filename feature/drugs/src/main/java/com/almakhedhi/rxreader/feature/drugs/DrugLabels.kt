package com.almakhedhi.rxreader.feature.drugs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.almakhedhi.rxreader.core.database.entity.DrugEntity

fun drugDisplayName(drug: DrugEntity): String =
    drug.tradeEn?.takeIf { it.isNotBlank() }
        ?: drug.tradeAr?.takeIf { it.isNotBlank() }
        ?: drug.scientific
        ?: ""

fun formLabelRes(form: String?): Int =
    when (form?.lowercase()) {
        "tablet" -> R.string.form_tablet
        "capsule" -> R.string.form_capsule
        "syrup" -> R.string.form_syrup
        "suspension" -> R.string.form_suspension
        "injection" -> R.string.form_injection
        "drops" -> R.string.form_drops
        "cream" -> R.string.form_cream
        "ointment" -> R.string.form_ointment
        "suppository" -> R.string.form_suppository
        "inhaler" -> R.string.form_inhaler
        "sachet" -> R.string.form_sachet
        else -> R.string.form_other
    }

@Composable
fun formLabel(form: String?): String = stringResource(formLabelRes(form))

fun dispensingLabelRes(code: String?): Int? =
    when (code?.lowercase()) {
        "rx" -> R.string.disp_rx
        "otc" -> R.string.disp_otc
        "controlled" -> R.string.disp_controlled
        else -> null
    }

@Composable
fun dispensingLabel(code: String?): String? =
    dispensingLabelRes(code)?.let { stringResource(it) }

/** Read-only dropdown shared by the filter card and the editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    allLabel: String? = null,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected ?: allLabel ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allLabel != null) {
                DropdownMenuItem(
                    text = { Text(allLabel) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
