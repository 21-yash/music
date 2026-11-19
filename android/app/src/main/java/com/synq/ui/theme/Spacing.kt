package com.synq.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing constants ensuring a clean whitespace rhythm (§4.6b).
 * Strictly follows the 4 / 8 / 12 / 16 / 20 / 24 dp rhythm.
 */
object Spacing {
    val xxs = 4.dp
    val xs  = 8.dp
    val s   = 12.dp
    val m   = 16.dp
    val l   = 20.dp
    val xl  = 24.dp

    // Semantic rhythm targets from §4.6:
    val screenHorizontal        = 20.dp
    val headerTop               = 12.dp
    val headerBottom            = 8.dp
    val sectionGap              = 24.dp
    val headerToContent         = 12.dp
    val rowGap                  = 8.dp
    val cardInnerPadding        = 16.dp
    val cardInnerPaddingCompact = 12.dp
}

