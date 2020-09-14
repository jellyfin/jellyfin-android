package org.jellyfin.mobile.model.state

sealed class CheckUrlState {
    object Unchecked : CheckUrlState()
    object Pending : CheckUrlState()
    object Success : CheckUrlState()
    class Error(val message: String?) : CheckUrlState()
}
