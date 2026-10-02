package com.musicsportsapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class.
 *
 * @HiltAndroidApp triggers Hilt's code generation for the dependency
 * injection container. All Hilt-injected components will use this
 * as the parent component.
 */
@HiltAndroidApp
class MusicSportsApp : Application()
