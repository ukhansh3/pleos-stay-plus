package com.pleosstay.app.workspace

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway

@Composable
internal fun rememberDocumentSession(context:Context, transfer:FileTransferViewModel,endpoint:String,
    manager:ModeStartManager,safety:WaitingSafetyGateway,study:Boolean):DocumentSessionOwner {
    val key="document-session:"+study+":"+endpoint+":"+System.identityHashCode(transfer)
    return viewModel(key=key,factory=remember(transfer,endpoint,study){object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T =
            DocumentSessionOwner(context,transfer,endpoint,manager,safety,study) as T
    }})
}
