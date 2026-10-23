package com.github.catppuccin.jetbrains_icons.settings.views

import com.github.catppuccin.jetbrains_icons.bundles.PluginSettingsBundle
import com.github.catppuccin.jetbrains_icons.settings.Variant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class SettingsIconPackView(private val currentVariant: String) : JPanel() {
  private val dropdown = ComboBox<Variant>()
  private val restartButton = JButton("Restart IDE")

  val variant: String
    get() = (dropdown.selectedItem as Variant).id

  init {
    drawDropdown()
    drawRestartButton()

    val form =
      FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Variant:"), dropdown, 1, false)
        .addTooltip("Apply the changes to switch the icon pack")
        .addComponentToRightColumn(restartButton)
        .panel

    add(form)

    layout = FlowLayout(FlowLayout.LEADING)
  }

  private fun drawDropdown() {
    for (variant in Variant.values()) {
      dropdown.addItem(variant)
    }
    dropdown.selectedItem = Variant.values().find { it.id == currentVariant }
    dropdown.alignmentY = Component.CENTER_ALIGNMENT
  }

  private fun drawRestartButton() {
    restartButton.addActionListener { restartIde() }
  }

  /**
   * Restarts the IDE.
   *
   * Switching the variant is applied immediately, so this is only needed for the rare surfaces that
   * keep a stale icon around.
   */
  private fun restartIde() {
    val confirmed =
      Messages.showYesNoDialog(
        PluginSettingsBundle.message("dialog.message.restart.ide"),
        PluginSettingsBundle.message("dialog.title.restart.ide"),
        PluginSettingsBundle.message("dialog.action.restart.yes"),
        PluginSettingsBundle.message("dialog.action.restart.cancel"),
        Messages.getWarningIcon(),
      ) == Messages.YES

    if (confirmed) {
      (ApplicationManager.getApplication() as ApplicationEx).restart(true)
    }
  }
}
