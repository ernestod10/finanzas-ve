package dev.tubolsillo.app.core.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Guarda la Activity en primer plano para poder lanzar el portal desde ella.
 * Lanzarlo con applicationContext obliga a FLAG_ACTIVITY_NEW_TASK, que mete la
 * pantalla en otra task y no devuelve al usuario a donde estaba.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null

    val activity: Activity? get() = current?.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
