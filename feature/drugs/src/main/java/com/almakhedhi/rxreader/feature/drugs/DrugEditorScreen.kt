package com.almakhedhi.rxreader.feature.drugs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.data.model.DrugDraft
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugEditorScreen(
    viewModel: DrugEditorViewModel,
    drugId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(drugId) { viewModel.load(drugId) }
    LaunchedEffect(state.savedId) { if (state.savedId != null) onSaved() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (drugId > 0) {
                                R.string.editor_title_edit
                            } else {
                                R.string.editor_title_new
                            },
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.drugs_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary)
            }
        } else {
            val draft = state.draft
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.nameError) {
                    Text(
                        text = stringResource(R.string.editor_name_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                EditorSection(title = stringResource(R.string.detail_section_names)) {
                    EditorField(draft.tradeEn, { value -> viewModel.update { it.copy(tradeEn = value) } }, R.string.fld_trade_en)
                    EditorField(draft.tradeAr, { value -> viewModel.update { it.copy(tradeAr = value) } }, R.string.fld_trade_ar)
                    EditorField(draft.scientific, { value -> viewModel.update { it.copy(scientific = value) } }, R.string.fld_scientific)
                    EditorField(draft.actives, { value -> viewModel.update { it.copy(actives = value) } }, R.string.fld_actives)
                    EditorField(draft.strength, { value -> viewModel.update { it.copy(strength = value) } }, R.string.fld_strength)
                    FormDropdown(
                        selected = draft.dosageForm,
                        onSelect = { value -> viewModel.update { it.copy(dosageForm = value) } },
                    )
                    EditorField(draft.route, { value -> viewModel.update { it.copy(route = value) } }, R.string.fld_route)
                }
                EditorSection(title = stringResource(R.string.detail_section_product)) {
                    EditorField(draft.manufacturer, { value -> viewModel.update { it.copy(manufacturer = value) } }, R.string.fld_manufacturer)
                    EditorField(draft.country, { value -> viewModel.update { it.copy(country = value) } }, R.string.fld_country)
                    EditorField(draft.agent, { value -> viewModel.update { it.copy(agent = value) } }, R.string.fld_agent)
                    EditorField(draft.regNo, { value -> viewModel.update { it.copy(regNo = value) } }, R.string.fld_reg_no)
                    EditorField(draft.barcode, { value -> viewModel.update { it.copy(barcode = value) } }, R.string.fld_barcode)
                    EditorField(draft.packSize, { value -> viewModel.update { it.copy(packSize = value) } }, R.string.fld_pack)
                    PriceField(
                        draft = draft,
                        onChange = { value -> viewModel.update { it.copy(price = value) } },
                    )
                    EditorField(draft.priceDate, { value -> viewModel.update { it.copy(priceDate = value) } }, R.string.fld_price_date)
                    DispensingDropdown(
                        selected = draft.dispensing,
                        onSelect = { value -> viewModel.update { it.copy(dispensing = value) } },
                    )
                }
                EditorSection(title = stringResource(R.string.detail_section_class)) {
                    EditorField(draft.therapeutic, { value -> viewModel.update { it.copy(therapeutic = value) } }, R.string.fld_therapeutic)
                    EditorField(draft.atc, { value -> viewModel.update { it.copy(atc = value) } }, R.string.fld_atc)
                }
                EditorSection(title = stringResource(R.string.detail_section_clinical)) {
                    EditorField(draft.indications, { value -> viewModel.update { it.copy(indications = value) } }, R.string.fld_indications, singleLine = false)
                    EditorField(draft.dosageAdults, { value -> viewModel.update { it.copy(dosageAdults = value) } }, R.string.fld_dose_adults, singleLine = false)
                    EditorField(draft.dosageChildren, { value -> viewModel.update { it.copy(dosageChildren = value) } }, R.string.fld_dose_children, singleLine = false)
                    EditorField(draft.contraindications, { value -> viewModel.update { it.copy(contraindications = value) } }, R.string.fld_contra, singleLine = false)
                    EditorField(draft.sideEffects, { value -> viewModel.update { it.copy(sideEffects = value) } }, R.string.fld_side, singleLine = false)
                    EditorField(draft.interactions, { value -> viewModel.update { it.copy(interactions = value) } }, R.string.fld_interactions, singleLine = false)
                    EditorField(draft.pregnancy, { value -> viewModel.update { it.copy(pregnancy = value) } }, R.string.fld_pregnancy, singleLine = false)
                    EditorField(draft.warnings, { value -> viewModel.update { it.copy(warnings = value) } }, R.string.fld_warnings, singleLine = false)
                    EditorField(draft.storage, { value -> viewModel.update { it.copy(storage = value) } }, R.string.fld_storage, singleLine = false)
                }
                EditorSection(title = stringResource(R.string.detail_section_system)) {
                    EditorField(draft.rxNormId, { value -> viewModel.update { it.copy(rxNormId = value) } }, R.string.fld_rxnorm)
                    EditorField(draft.dailymedId, { value -> viewModel.update { it.copy(dailymedId = value) } }, R.string.fld_dailymed)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = draft.reviewed,
                            onCheckedChange = { value -> viewModel.update { it.copy(reviewed = value) } },
                        )
                        Text(
                            text = stringResource(R.string.fld_reviewed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkPrimary,
                        )
                    }
                }
                GradientButton(
                    text = stringResource(
                        if (state.saving) R.string.editor_saving else R.string.editor_save,
                    ),
                    onClick = { viewModel.save() },
                    enabled = !state.saving,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TealDark,
            )
            content()
        }
    }
}

@Composable
private fun EditorField(
    value: String?,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value ?: "",
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun PriceField(draft: DrugDraft, onChange: (Double?) -> Unit) {
    var text by remember(draft.price) {
        mutableStateOf(draft.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.trim().replace(",", "").toDoubleOrNull()?.takeIf { price -> price >= 0 })
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.fld_price)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun FormDropdown(selected: String?, onSelect: (String?) -> Unit) {
    val codes = listOf(
        "tablet", "capsule", "syrup", "suspension", "injection", "drops",
        "cream", "ointment", "suppository", "inhaler", "sachet", "other",
    )
    val labels = codes.map { stringResource(formLabelRes(it)) }
    val selectedLabel = selected?.let { labels[codes.indexOf(it).takeIf { i -> i >= 0 } ?: 11] }
    OptionDropdown(
        label = stringResource(R.string.fld_form),
        options = labels,
        selected = selectedLabel,
        onSelect = { label -> onSelect(label?.let { codes[labels.indexOf(it)] }) },
    )
}

@Composable
private fun DispensingDropdown(selected: String?, onSelect: (String?) -> Unit) {
    val codes = listOf("rx", "otc", "controlled")
    val labels = listOf(
        stringResource(R.string.disp_rx),
        stringResource(R.string.disp_otc),
        stringResource(R.string.disp_controlled),
    )
    val selectedLabel = selected?.let { labels.getOrNull(codes.indexOf(it)) }
    OptionDropdown(
        label = stringResource(R.string.fld_dispensing),
        options = labels,
        selected = selectedLabel,
        allLabel = stringResource(R.string.drugs_all),
        onSelect = { label -> onSelect(label?.let { codes[labels.indexOf(it)] }) },
    )
}
