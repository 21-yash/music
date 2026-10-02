package com.musicsportsapp.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicsportsapp.core.domain.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Common UI state for screens.
 */
data class UiState<T>(
    val isLoading: Boolean = false,
    val data: T? = null,
    val error: AppError? = null,
)

/**
 * Base ViewModel providing standard state management.
 */
abstract class BaseViewModel<T>(initialData: T? = null) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState(data = initialData))
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()

    protected fun updateData(data: T) {
        _uiState.update { it.copy(data = data, error = null) }
    }

    protected fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    protected fun setError(error: AppError) {
        _uiState.update { it.copy(isLoading = false, error = error) }
    }

    protected fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Helper to run an async operation with loading state.
     */
    protected fun launchWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            setLoading(true)
            try {
                block()
            } finally {
                setLoading(false)
            }
        }
    }
}
