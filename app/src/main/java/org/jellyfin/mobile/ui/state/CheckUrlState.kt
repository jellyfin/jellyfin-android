package org.jellyfin.mobile.ui.state

sealed class CheckUrlState {
    object Unchecked : CheckUrlState()
    object Pending : CheckUrlState()
    class Success(val address: String) : CheckUrlState()
    class Error(val message: String?) : CheckUrlState()
}
