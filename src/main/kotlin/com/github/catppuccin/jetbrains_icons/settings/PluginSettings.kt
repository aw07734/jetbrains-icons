package com.github.catppuccin.jetbrains_icons.settings

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import javax.swing.JComponent

class PluginSettings : Configurable {
  private val component = PluginSettingsComponent(PluginSettingsState.instance.variant)

  override fun createComponent(): JComponent = component.view

  override fun isModified(): Boolean {
    val state = PluginSettingsState.instance
    return packChanged() ||
      component.additionalSupport.python.isSelected != state.pythonSupport ||
      component.additionalSupport.java.isSelected != state.javaSupport ||
      component.additionalSupport.go.isSelected != state.goSupport
  }

  override fun apply() {
    val state = PluginSettingsState.instance
    val packChanged = packChanged()
    val supportChanged =
      component.additionalSupport.python.isSelected != state.pythonSupport ||
        component.additionalSupport.java.isSelected != state.javaSupport ||
        component.additionalSupport.go.isSelected != state.goSupport

    state.pythonSupport = component.additionalSupport.python.isSelected
    state.javaSupport = component.additionalSupport.java.isSelected
    state.goSupport = component.additionalSupport.go.isSelected
    if (packChanged) state.variant = component.iconPack.variant

    if (packChanged || supportChanged) {
      refreshIcons()
    }
  }

  override fun getDisplayName(): String = "Catppuccin Icons"

  private fun packChanged(): Boolean {
    val state = PluginSettingsState.instance
    return component.iconPack.variant != state.variant
  }

  /**
   * Applies the changed icon pack to the running IDE.
   *
   * The path patchers read the settings on every call, but the platform caches the icons it
   * resolved from them, so the cache has to be dropped for the new variant to take effect. The UI
   * is repainted afterwards so that every surface picks the new icons up.
   */
  private fun refreshIcons() {
    IconLoader.clearCache()
    LafManager.getInstance().updateUI()

    for (project in ProjectManager.getInstance().openProjects) {
      val projectView = ProjectView.getInstance(project)
      projectView?.refresh()
      projectView?.currentProjectViewPane?.updateFromRoot(true)
    }
  }
}
