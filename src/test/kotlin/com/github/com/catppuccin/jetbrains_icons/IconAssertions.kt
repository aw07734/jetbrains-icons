package com.github.com.catppuccin.jetbrains_icons

import javax.swing.Icon
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Asserts that [actual] is the same icon as [expected].
 *
 * The providers resolve icons through [com.github.catppuccin.jetbrains_icons.IconPack.icons], while
 * the tests build their own [com.github.catppuccin.jetbrains_icons.Icons] instance. Both ask
 * `IconLoader` for the same resource, but the platform version decides what comes back: older
 * platforms (2023.1, the one CI builds against) cache the lookup and hand out the very same
 * instance, while newer ones (2024.2+) create a new `CachedImageIcon` per lookup. Comparing
 * instances therefore only works on the older platforms, and `CachedImageIcon.equals` cannot be
 * used either because on newer platforms it also compares lambda-valued fields such as the tooltip
 * supplier.
 *
 * Comparing the resource path expresses what the test actually cares about and stays stable across
 * platform versions. The path is read reflectively because the accessor that exposes it
 * (`IconPathProvider.getOriginalPath`) only exists on newer platforms; where it is missing, the
 * platform's own equality already compares the path and is used as the fallback.
 *
 * @param expected the icon that is expected to be returned.
 * @param actual the icon that was returned.
 */
fun assertIconEquals(expected: Icon, actual: Icon?) {
  val expectedPath = iconPath(expected)
  val actualPath = iconPath(actual)

  if (expectedPath != null && actualPath != null) {
    assertEquals(expectedPath, actualPath)
  } else {
    assertEquals(expected, actual)
  }
}

/**
 * Returns the resource path of [icon], or `null` if the platform does not expose one.
 *
 * The accessor is looked up reflectively so that this helper compiles against every supported
 * platform, including 2023.1 where `IconPathProvider` does not exist yet.
 */
private fun iconPath(icon: Icon?): String? {
  if (icon == null) return null

  val getter =
    icon.javaClass.methods.firstOrNull { it.name == "getOriginalPath" && it.parameterCount == 0 }

  return runCatching { getter?.invoke(icon) as? String }.getOrNull()
}
