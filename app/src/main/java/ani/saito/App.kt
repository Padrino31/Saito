package ani.saito

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import ani.saito.aniyomi.anime.custom.AppModule
import ani.saito.aniyomi.anime.custom.PreferenceModule
import ani.saito.connections.comments.CommentsAPI
import ani.saito.connections.crashlytics.CrashlyticsInterface
import ani.saito.notifications.TaskScheduler
import ani.saito.others.DisabledReports
import ani.saito.parsers.AnimeSources
import ani.saito.parsers.MangaSources
import ani.saito.parsers.NovelSources
import ani.saito.parsers.novel.NovelExtensionManager
import ani.saito.settings.SettingsActivity
import ani.saito.settings.saving.PrefManager
import ani.saito.settings.saving.PrefName
import ani.saito.util.FinalExceptionHandler
import ani.saito.util.Logger
import com.google.android.material.color.DynamicColors
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get


@SuppressLint("StaticFieldLeak")
class App : MultiDexApplication() {
    private lateinit var animeExtensionManager: AnimeExtensionManager
    private lateinit var mangaExtensionManager: MangaExtensionManager
    private lateinit var novelExtensionManager: NovelExtensionManager
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    init {
        instance = this
    }

    val mFTActivityLifecycleCallbacks = FTActivityLifecycleCallbacks()

    override fun onCreate() {
        super.onCreate()

        PrefManager.init(this)
        Injekt.importModule(AppModule(this))
        Injekt.importModule(PreferenceModule(this))

        val crashlytics = Injekt.get<CrashlyticsInterface>()
        crashlytics.initialize(this)

        val useMaterialYou: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        if (useMaterialYou) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        registerActivityLifecycleCallbacks(mFTActivityLifecycleCallbacks)

        crashlytics.setCrashlyticsCollectionEnabled(!DisabledReports)
        (PrefManager.getVal(PrefName.SharedUserID) as Boolean).let {
            if (!it) return@let
            val dUsername = PrefManager.getVal(PrefName.DiscordUserName, null as String?)
            val aUsername = PrefManager.getVal(PrefName.AnilistUserName, null as String?)
            if (dUsername != null) {
                crashlytics.setCustomKey("dUsername", dUsername)
            }
            if (aUsername != null) {
                crashlytics.setCustomKey("aUsername", aUsername)
            }
        }
        crashlytics.setCustomKey("device Info", SettingsActivity.getDeviceInfo())

        Logger.init(this)
        Thread.setDefaultUncaughtExceptionHandler(FinalExceptionHandler())
        Logger.log("App: Logging started")

        initializeNetwork(baseContext)

        setupNotificationChannels()
        if (!LogcatLogger.isInstalled) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }

        animeExtensionManager = Injekt.get()
        mangaExtensionManager = Injekt.get()
        novelExtensionManager = Injekt.get()

        val animeScope = CoroutineScope(Dispatchers.Default)
        animeScope.launch {
            animeExtensionManager.findAvailableExtensions()
            Logger.log("Anime Extensions: ${animeExtensionManager.installedExtensionsFlow.first()}")
            AnimeSources.init(animeExtensionManager.installedExtensionsFlow)
        }
        val mangaScope = CoroutineScope(Dispatchers.Default)
        mangaScope.launch {
            mangaExtensionManager.findAvailableExtensions()
            Logger.log("Manga Extensions: ${mangaExtensionManager.installedExtensionsFlow.first()}")
            MangaSources.init(mangaExtensionManager.installedExtensionsFlow)
        }
        val novelScope = CoroutineScope(Dispatchers.Default)
        novelScope.launch {
            novelExtensionManager.findAvailableExtensions()
            Logger.log("Novel Extensions: ${novelExtensionManager.installedExtensionsFlow.first()}")
            NovelSources.init(novelExtensionManager.installedExtensionsFlow)
        }
        val commentsScope = CoroutineScope(Dispatchers.Default)
        commentsScope.launch {
            CommentsAPI.fetchAuthToken()
        }

        val useAlarmManager = PrefManager.getVal<Boolean>(PrefName.UseAlarmManager)
        TaskScheduler.create(this, useAlarmManager).scheduleAllTasks(this)
    }

    private fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            Logger.log("Failed to modify notification channels")
            Logger.log(e)
        }
    }

    inner class FTActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        var currentActivity: Activity? = null
        override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
        override fun onActivityStarted(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityResumed(p0: Activity) {
            currentActivity = p0
        }

        override fun onActivityPaused(p0: Activity) {}
        override fun onActivityStopped(p0: Activity) {}
        override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
        override fun onActivityDestroyed(p0: Activity) {}
    }

    companion object {
        private var instance: App? = null
        var context: Context? = null
        fun currentContext(): Context? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity ?: context
        }

        fun currentActivity(): Activity? {
            return instance?.mFTActivityLifecycleCallbacks?.currentActivity
        }
    }
}