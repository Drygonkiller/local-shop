package com.quickbill.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quickbill.app.R
import com.quickbill.app.calculator.CalculatorDraft
import com.quickbill.app.calculator.CalculatorKey
import com.quickbill.app.data.formatNpr

@Composable
internal fun CalculatorScreen(
    draft: CalculatorDraft,
    enabled: Boolean,
    onKey: (CalculatorKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val keyHeight = if (maxHeight >= 420.dp) 64.dp else 52.dp
        val spacing = 8.dp
        val content = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = spacing)
        if (maxWidth >= 560.dp) {
            Row(content, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    CalculatorDisplay(draft)
                    CalculatorHint()
                }
                CalculatorKeypad(
                    draft, enabled, onKey, keyHeight,
                    modifier = Modifier.weight(1.45f).verticalScroll(rememberScrollState()),
                )
            }
        } else {
            Column(
                content.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                CalculatorDisplay(draft)
                CalculatorKeypad(draft, enabled, onKey, keyHeight, Modifier.fillMaxWidth())
                CalculatorHint()
            }
        }
    }
}

@Composable
private fun CalculatorDisplay(draft: CalculatorDraft) {
    val expression = remember(draft.committedAmounts) {
        draft.committedAmounts.joinToString(separator = " + ", postfix = " +", transform = ::formatNpr)
    }
    val entry = draft.entry.ifEmpty { "0" }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BilingualLabel(R.string.calculator_amount, R.string.calculator_amount_ne)
                Text(
                    text = "Rs. $entry",
                    modifier = Modifier.weight(1f),
                    style = if (entry.length > 8) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (draft.committedAmounts.isNotEmpty()) {
                    Text(
                        text = expression,
                        modifier = Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculatorHint() {
    BilingualLabel(
        R.string.calculator_hint,
        R.string.calculator_hint_ne,
        mainStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun CalculatorKeypad(
    draft: CalculatorDraft,
    enabled: Boolean,
    onKey: (CalculatorKey) -> Unit,
    keyHeight: Dp,
    modifier: Modifier,
) {
    val spacing = 6.dp
    val hasAmounts = draft.entry.isNotEmpty() || draft.committedAmounts.isNotEmpty()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (digits in listOf("789", "456", "123")) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (digit in digits) {
                        SymbolKey(
                            symbol = digit.toString(),
                            enabled = enabled,
                            onClick = { onKey(CalculatorKey.Digit(digit)) },
                            modifier = Modifier.weight(1f).heightIn(min = keyHeight),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                SymbolKey(
                    symbol = "0",
                    enabled = enabled,
                    onClick = { onKey(CalculatorKey.Digit('0')) },
                    modifier = Modifier.weight(2f).heightIn(min = keyHeight),
                )
                SymbolKey(
                    symbol = ".",
                    description = R.string.calculator_decimal,
                    enabled = enabled && '.' !in draft.entry,
                    onClick = { onKey(CalculatorKey.Decimal) },
                    modifier = Modifier.weight(1f).heightIn(min = keyHeight),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) {
            SymbolKey(
                symbol = "⌫",
                description = R.string.calculator_backspace,
                enabled = enabled && hasAmounts,
                onClick = { onKey(CalculatorKey.Backspace) },
                modifier = Modifier.fillMaxWidth().heightIn(min = keyHeight),
            )
            KeyButton(
                onClick = { onKey(CalculatorKey.Clear) },
                enabled = enabled && hasAmounts,
                modifier = Modifier.fillMaxWidth().heightIn(min = keyHeight),
            ) {
                BilingualLabel(
                    R.string.calculator_clear,
                    R.string.calculator_clear_ne,
                    mainStyle = MaterialTheme.typography.labelMedium,
                    centered = true,
                )
            }
            SymbolKey(
                symbol = "+",
                description = R.string.calculator_plus,
                enabled = enabled && draft.entry.isNotEmpty(),
                onClick = { onKey(CalculatorKey.Plus) },
                modifier = Modifier.fillMaxWidth().heightIn(min = keyHeight * 2 + spacing),
                emphasized = true,
            )
        }
    }
}

@Composable
private fun SymbolKey(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    @StringRes description: Int? = null,
    emphasized: Boolean = false,
) {
    val label = description?.let { stringResource(it) }
    KeyButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (label == null) modifier else modifier.semantics { contentDescription = label },
        emphasized = emphasized,
    ) {
        Text(
            text = symbol,
            modifier = if (label == null) Modifier else Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun KeyButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    emphasized: Boolean = false,
    label: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) colors.primary else colors.surfaceContainerHigh,
            contentColor = if (emphasized) colors.onPrimary else colors.onSurface,
        ),
    ) { label() }
}
