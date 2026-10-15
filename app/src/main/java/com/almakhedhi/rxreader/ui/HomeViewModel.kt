package com.almakhedhi.rxreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almakhedhi.rxreader.core.data.repo.PrescriptionRepository
import com.almakhedhi.rxreader.core.database.entity.PrescriptionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Home screen data: the latest prescriptions for the recents card. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: PrescriptionRepository,
) : ViewModel() {

    val recent: StateFlow<List<PrescriptionSummary>> =
        repository.observeRecent(3)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
