package app.chenadet

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.chenadet.data.*
import app.chenadet.location.ForegroundLocation
import app.chenadet.presentation.MainViewModel

class CheNadetApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class AppContainer(context: Context) {
    private val store = AppStore(context)
    private val repository = DefaultWeatherRepository(WeatherApi(), store)
    private val location = ForegroundLocation(context)
    val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(repository, store, location) as T
        }
    }
}
