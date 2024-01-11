package io.sebi.windowmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun XPButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clickable { onClick() }.shadow(2.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(3.dp))
            .background(Color(0xFFF4F4F0)).padding(10.dp)
    ) {
        Text(text)
    }
}