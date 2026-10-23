package com.github.com.catppuccin.jetbrains_icons

import com.intellij.ui.icons.IconPathProvider
import javax.swing.Icon
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Asserts that [actual] is the same icon as [expected].
 *
 * The providers resolve icons through [com.github.catppuccin.jetbrains_icons.IconPack.icons], while
 * the tests build their own [com.github.catppuccin.jetbrains_icons.Icons] instance. Both ask
 * `IconLoader` for the same resource, but the platform hands out distinct `CachedImageIcon` objects
 * depending on when the icon cache was seeded, and `CachedImageIcon.equals` also compares
 * lambda-valued fields such as the tooltip supplier. Comparing the resource path expresses what the
 * test actually cares about and stays stable across platform versions.
 *
 * @param expected the icon that is expected to be returned.
 * @param actual the icon that was returned.
 */
fun assertIconEquals(expected: Icon, actual: Icon?) {
  assertEquals(iconPath(expected), iconPath(actual))
}

/** Returns the resource path of [icon], or `null` if the icon does not expose one. */
private fun iconPath(icon: Icon?): String? = (icon as? IconPathProvider)?.originalPath
