package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.*

@Composable
public fun QuestionModal(
    question: String,
    options: List<String>,
    isMultiSelect: Boolean = false,
    onSubmit: (selectedOptions: List<String>, writeIn: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedOptions by remember { mutableStateOf(setOf<String>()) }
    var writeInText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, AntigravityViolet.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AntigravityVioletDark)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "ANTIGRAVITY DECISION",
                    color = AntigravityVioletLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont
                )
            }
        }

        // Question Title
        Text(
            text = question,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp
        )

        // Options List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = selectedOptions.contains(option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AntigravityViolet.copy(alpha = 0.15f) else DarkSurfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) AntigravityViolet else DarkBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            selectedOptions = if (isMultiSelect) {
                                if (isSelected) selectedOptions - option else selectedOptions + option
                            } else {
                                setOf(option)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isMultiSelect) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = AntigravityViolet,
                                uncheckedColor = TextSecondary
                            )
                        )
                    } else {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = AntigravityViolet,
                                unselectedColor = TextSecondary
                            )
                        )
                    }
                    Text(
                        text = option,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        // Write-in field
        OutlinedTextField(
            value = writeInText,
            onValueChange = { writeInText = it },
            placeholder = { Text("Other response or custom feedback...", fontSize = 13.sp, color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = AntigravityViolet,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        // Submit Button
        Button(
            onClick = {
                onSubmit(
                    selectedOptions.toList(),
                    writeInText.ifBlank { null }
                )
            },
            enabled = selectedOptions.isNotEmpty() || writeInText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityViolet,
                contentColor = Color.White,
                disabledContainerColor = DarkBorder,
                disabledContentColor = TextMuted
            )
        ) {
            Text(
                text = "Submit Decision",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
