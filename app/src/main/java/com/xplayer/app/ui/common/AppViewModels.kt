package com.xplayer.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xplayer.app.AppContainer
import com.xplayer.app.XPlayerApp

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as XPlayerApp).container

class SimpleVMFactory<VM : ViewModel>(private val creator: () -> VM) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

@Composable
inline fun <reified VM : ViewModel> rememberAppVM(
    key: String? = null,
    crossinline creator: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    val factory = remember { SimpleVMFactory { creator(container) } }
    return viewModel(key = key, factory = factory)
}
