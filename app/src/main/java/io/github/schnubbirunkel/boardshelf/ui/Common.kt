package io.github.schnubbirunkel.boardshelf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.schnubbirunkel.boardshelf.BoardshelfApp
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Repository
import io.github.schnubbirunkel.boardshelf.net.BggAuthException
import io.github.schnubbirunkel.boardshelf.net.BggTokenMissingException
import io.github.schnubbirunkel.boardshelf.net.HttpException
import java.io.IOException
import java.net.UnknownHostException

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (Repository) -> VM): VM {
    val app = LocalContext.current.applicationContext as BoardshelfApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.repository) } })
}

fun Context.app(): BoardshelfApp = applicationContext as BoardshelfApp

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun Context.errorText(t: Throwable): String = when (t) {
    is io.github.schnubbirunkel.boardshelf.data.CoverTooSmallException -> getString(R.string.err_cover_small, t.w, t.h)
    is BggTokenMissingException -> getString(R.string.err_bgg_token_missing)
    is BggAuthException -> getString(R.string.err_bgg_auth)
    is UnknownHostException -> getString(R.string.err_offline)
    is HttpException -> getString(R.string.err_http, t.code)
    is IOException -> t.message ?: getString(R.string.err_generic)
    else -> t.message ?: getString(R.string.err_generic)
}
