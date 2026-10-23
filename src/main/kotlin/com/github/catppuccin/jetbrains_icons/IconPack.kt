package com.github.catppuccin.jetbrains_icons

import com.github.catppuccin.jetbrains_icons.settings.PluginSettingsState
import java.util.concurrent.ConcurrentHashMap

object IconPack {
  private val instances = ConcurrentHashMap<String, Icons>()

  /**
   * The icons for the currently selected [PluginSettingsState.variant].
   *
   * The icons are resolved per variant so that switching the variant takes effect immediately,
   * without an IDE restart. Instances are cached because [Icons] holds one icon per generated file
   * and the platform caches the icon lookups anyway.
   */
  val icons: Icons
    get() = instances.computeIfAbsent(PluginSettingsState.instance.variant) { Icons(it) }
}
