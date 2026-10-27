package com.diaroutine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.diaroutine.ui.theme.DiaShapes
import com.diaroutine.ui.theme.DiaTheme

data class NavItem<T>(val key: T, val label: String, val icon: ImageVector)

/**
 * The floating pill of round tab buttons from the screenshot. Icons only, so each
 * button carries its label for screen readers.
 */
@Composable
fun <T> FloatingNavBar(
    items: List<NavItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DiaTheme.colors
    Row(
        modifier = modifier
            .shadow(18.dp, DiaShapes.pill, ambientColor = colors.primary.copy(alpha = 0.25f), spotColor = colors.primary.copy(alpha = 0.25f))
            .clip(DiaShapes.pill)
            .background(colors.card)
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val isSelected = item.key == selected
            val container by animateColorAsState(if (isSelected) colors.primary else colors.cardStrong, label = "nav-container")
            val tint by animateColorAsState(if (isSelected) colors.onPrimary else colors.primaryStrong, label = "nav-tint")
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item.key) })
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
    }
}
