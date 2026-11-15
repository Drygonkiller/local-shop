package com.quickbill.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quickbill.app.QuickBillUiState
import com.quickbill.app.R
import com.quickbill.app.calculator.CalculatorKey
import com.quickbill.app.data.Bill
import com.quickbill.app.data.DailySales
import com.quickbill.app.data.FavouriteItem
import com.quickbill.app.data.SavedBill
import com.quickbill.app.data.formatNpr
import com.quickbill.app.printing.ThermalPrinting
import kotlinx.coroutines.flow.Flow

private enum class Screen { Home, Bill, History, SavedBill }
private enum class BillingMode(@param:StringRes val label: Int, @param:StringRes val nepali: Int) {
    Favourites(R.string.favourites, R.string.favourites_ne),
    Calculator(R.string.mode_calculator, R.string.mode_calculator_ne),
    Today(R.string.report_today, R.string.report_today_ne),
}
private enum class EntryDialog { AddItem, Shop, ShopForPrint, ShopForShare }
private enum class BillFooterAction(@param:StringRes val label: Int, @param:StringRes val nepali: Int) {
    ViewBill(R.string.view_bill, R.string.view_bill_ne),
    AddItems(R.string.add_items, R.string.add_items_ne),
}

private class OpenLocalPhoto : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
}

@Composable
internal fun QuickBillApp(
    state: QuickBillUiState,
    history: Flow<List<Bill>>,
    dailySales: Flow<DailySales>,
    thermalPrinting: ThermalPrinting,
    onAddFavorite: (String, Long, (Boolean) -> Unit) -> Unit,
    onEditFavourite: (Long, String, Long, (Boolean) -> Unit) -> Unit,
    onDeleteFavourite: (Long, (Boolean) -> Unit) -> Unit,
    onAddToBill: (FavouriteItem) -> Unit,
    onChangeQuantity: (Long, Int) -> Unit,
    onPrintBill: () -> Unit,
    onCalculatorKey: (CalculatorKey) -> Unit,
    onPrintCalculatorBill: () -> Unit,
    onSaveShop: (String, String, (Boolean) -> Unit) -> Unit,
    onReprint: (Long) -> Unit,
    loadSavedBill: suspend (Long) -> SavedBill?,
    onShareSavedBill: (Long) -> Unit,
    onShareDraftBill: () -> Unit,
    onPrintReport: (DailySales) -> Unit,
    onSetPhoto: (Long, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var mode by rememberSaveable { mutableStateOf(BillingMode.Favourites) }
    var openedBillNumber by rememberSaveable { mutableStateOf<Long?>(null) }
    var returnTo by rememberSaveable { mutableStateOf(Screen.Home) }
    var entryDialog by rememberSaveable { mutableStateOf<EntryDialog?>(null) }
    var showThermalSettings by rememberSaveable { mutableStateOf(false) }
    var favouriteMenuId by rememberSaveable { mutableStateOf<Long?>(null) }
    var photoTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val printerState by thermalPrinting.state.collectAsStateWithLifecycle()
    val printerBusy = printerState.printing || printerState.askingPermission
    val photoContract = remember { OpenLocalPhoto() }
    val photoPicker = rememberLauncherForActivityResult(photoContract) { uri ->
        val itemId = photoTargetId
        photoTargetId = null
        if (uri != null && itemId != null) {
            val permission = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (permission.isSuccess) {
                onSetPhoto(itemId, uri.toString())
            } else {
                Toast.makeText(context, R.string.photo_access_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickPhoto: (Long) -> Unit = { id ->
        photoTargetId = id
        try {
            photoPicker.launch(arrayOf("image/*"))
        } catch (_: ActivityNotFoundException) {
            photoTargetId = null
            Toast.makeText(context, R.string.photo_access_error, Toast.LENGTH_SHORT).show()
        }
    }
    val inCalculator = screen == Screen.Home && mode == BillingMode.Calculator
    val inToday = screen == Screen.Home && mode == BillingMode.Today
    val openBill: (Long) -> Unit = { number ->
        returnTo = screen
        openedBillNumber = number
        screen = Screen.SavedBill
    }
    val goBack: () -> Unit = {
        when (screen) {
            Screen.SavedBill -> {
                screen = returnTo
                openedBillNumber = null
            }
            Screen.Home -> mode = BillingMode.Favourites
            Screen.Bill, Screen.History -> screen = Screen.Home
        }
    }
    val checkout = if (inCalculator) onPrintCalculatorBill else onPrintBill
    val requestPrint: () -> Unit = {
        if (state.shop == null) entryDialog = EntryDialog.ShopForPrint else checkout()
    }
    val requestShare: () -> Unit = {
        if (state.shop == null) entryDialog = EntryDialog.ShopForShare else onShareDraftBill()
    }
    BackHandler(enabled = screen != Screen.Home || mode != BillingMode.Favourites, onBack = goBack)
    LaunchedEffect(state.lines.isEmpty()) {
        if (screen == Screen.Bill && state.lines.isEmpty()) screen = Screen.Home
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Header(
                screen = screen,
                canEditShop = !state.isLoading,
                onBack = goBack,
                onShop = { entryDialog = EntryDialog.Shop },
                onHistory = { screen = Screen.History },
            )
            if (screen == Screen.Home) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BillingTabs(
                        selected = mode,
                        enabled = !state.isSavingBill,
                        onSelect = { mode = it },
                        modifier = Modifier.widthIn(max = 760.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val bodyModifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
                when (screen) {
                    Screen.Home -> when (mode) {
                        BillingMode.Calculator -> CalculatorScreen(
                            draft = state.calculator,
                            enabled = !state.isSavingBill,
                            onKey = onCalculatorKey,
                            modifier = bodyModifier,
                        )
                        BillingMode.Favourites -> FavouritesScreen(
                            favourites = state.favourites,
                            lines = state.lines,
                            isLoading = state.isLoading,
                            canAddToBill = !state.isSavingBill,
                            onAddItem = { entryDialog = EntryDialog.AddItem },
                            onTap = onAddToBill,
                            onManageFavourite = { item -> favouriteMenuId = item.id },
                            modifier = bodyModifier,
                        )
                        BillingMode.Today -> {
                            val report by dailySales.collectAsStateWithLifecycle(initialValue = null)
                            DailySalesScreen(
                                report = report,
                                onOpenBill = openBill,
                                onPrintReport = onPrintReport,
                                canPrint = !printerBusy && !state.isSavingBill,
                                modifier = bodyModifier,
                            )
                        }
                    }
                    Screen.Bill -> BillingScreen(
                        lines = state.lines,
                        canChangeQuantity = !state.isSavingBill,
                        onChangeQuantity = onChangeQuantity,
                        modifier = bodyModifier,
                    )
                    Screen.History -> {
                        val savedBills by history.collectAsStateWithLifecycle(initialValue = emptyList())
                        HistoryScreen(
                            history = savedBills,
                            onReprint = onReprint,
                            onOpenBill = openBill,
                            onShare = onShareSavedBill,
                            canReprint = !printerBusy && !state.isSavingBill,
                            modifier = bodyModifier,
                        )
                    }
                    Screen.SavedBill -> openedBillNumber?.let { number ->
                        key(number) {
                            var bill by remember { mutableStateOf<SavedBill?>(null) }
                            var hasLoaded by remember { mutableStateOf(false) }
                            LaunchedEffect(number) {
                                bill = loadSavedBill(number)
                                hasLoaded = true
                            }
                            SavedBillScreen(
                                bill = bill,
                                onShare = onShareSavedBill,
                                onReprint = onReprint,
                                canReprint = !printerBusy && !state.isSavingBill,
                                isBillMissing = hasLoaded && bill == null,
                                modifier = bodyModifier,
                            )
                        }
                    }
                }
            }
            if ((screen == Screen.Home && !inToday) || screen == Screen.Bill) {
                BillFooter(
                    totalPaisa = if (inCalculator) state.calculator.totalPaisa else state.totalPaisa,
                    canPrint = !state.isLoading && if (inCalculator) {
                        state.calculator.canPrint
                    } else {
                        state.lines.isNotEmpty()
                    },
                    isSaving = state.isSavingBill,
                    isPrinting = printerBusy,
                    secondaryAction = when {
                        inCalculator -> null
                        screen == Screen.Bill -> BillFooterAction.AddItems
                        else -> BillFooterAction.ViewBill
                    },
                    onSecondaryClick = {
                        screen = if (screen == Screen.Home) Screen.Bill else Screen.Home
                    },
                    onShare = if (screen == Screen.Bill) requestShare else null,
                    onPrint = requestPrint,
                )
            }
        }
    }

    when (entryDialog) {
        EntryDialog.AddItem -> ItemEditorDialog(
            onDismiss = { entryDialog = null },
            onSave = onAddFavorite,
        )
        EntryDialog.Shop, EntryDialog.ShopForPrint, EntryDialog.ShopForShare -> ShopDialog(
            shop = state.shop,
            forPrint = entryDialog == EntryDialog.ShopForPrint,
            forShare = entryDialog == EntryDialog.ShopForShare,
            onDismiss = { entryDialog = null },
            onSave = onSaveShop,
            onThermalPrinter = if (entryDialog == EntryDialog.Shop) {
                { showThermalSettings = true }
            } else {
                null
            },
            onSaved = {
                val completed = entryDialog
                entryDialog = null
                when (completed) {
                    EntryDialog.ShopForPrint -> checkout()
                    EntryDialog.ShopForShare -> onShareDraftBill()
                    else -> Unit
                }
            },
        )
        null -> Unit
    }

    state.favourites.firstOrNull { it.id == favouriteMenuId }?.let { item ->
        FavouriteActions(
            item = item,
            onDismiss = { favouriteMenuId = null },
            onEdit = onEditFavourite,
            onDelete = onDeleteFavourite,
            onPickPhoto = pickPhoto,
            onRemovePhoto = { id -> onSetPhoto(id, null) },
        )
    }
    if (showThermalSettings) {
        ThermalPrinterSettings(
            printing = thermalPrinting,
            state = printerState,
            shop = state.shop,
            onDismiss = { showThermalSettings = false },
        )
    }
    ThermalPrinterPrompts(thermalPrinting, printerState)
}

@Composable
private fun BillingTabs(
    selected: BillingMode,
    enabled: Boolean,
    onSelect: (BillingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        for (mode in BillingMode.entries) {
            Tab(
                selected = selected == mode,
                enabled = enabled,
                onClick = { onSelect(mode) },
                text = { BilingualLabel(mode.label, mode.nepali, centered = true) },
            )
        }
    }
}

@Composable
private fun Header(
    screen: Screen,
    canEditShop: Boolean,
    onBack: () -> Unit,
    onShop: () -> Unit,
    onHistory: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (screen != Screen.Home) {
                    val backLabel = stringResource(R.string.back)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                    ) { BillIcon(BillIcon.Back) }
                }
                val title = when (screen) {
                    Screen.Home -> R.string.app_name to R.string.app_name_ne
                    Screen.Bill -> R.string.current_bill to R.string.current_bill_ne
                    Screen.History -> R.string.history to R.string.history_ne
                    Screen.SavedBill -> R.string.saved_bill_title to R.string.saved_bill_title_ne
                }
                BilingualLabel(
                    title.first,
                    title.second,
                    modifier = Modifier.weight(1f)
                        .padding(start = if (screen == Screen.Home) 8.dp else 0.dp)
                        .semantics { heading() },
                    mainStyle = MaterialTheme.typography.headlineSmall,
                    secondaryStyle = MaterialTheme.typography.bodySmall,
                )
                if (screen == Screen.Home) {
                    HeaderAction(R.string.history, R.string.history_ne, BillIcon.History, onHistory)
                    HeaderAction(R.string.shop, R.string.shop_ne, BillIcon.Shop, onShop, enabled = canEditShop)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HeaderAction(
    @StringRes title: Int,
    @StringRes nepali: Int,
    icon: BillIcon,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.widthIn(min = 64.dp).heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BillIcon(icon, size = 20.dp)
            BilingualLabel(title, nepali, centered = true, mainStyle = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BillFooter(
    totalPaisa: Long,
    canPrint: Boolean,
    isSaving: Boolean,
    isPrinting: Boolean,
    secondaryAction: BillFooterAction?,
    onSecondaryClick: () -> Unit,
    onShare: (() -> Unit)?,
    onPrint: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BilingualLabel(
                        R.string.bill_total,
                        R.string.bill_total_ne,
                        modifier = Modifier.weight(1f),
                        mainStyle = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatNpr(totalPaisa),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (secondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryClick,
                            enabled = !isSaving && (canPrint || secondaryAction == BillFooterAction.AddItems),
                            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = if (onShare == null) PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            else PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            BilingualLabel(secondaryAction.label, secondaryAction.nepali, centered = true)
                        }
                    }
                    if (onShare != null) {
                        OutlinedButton(
                            onClick = onShare,
                            enabled = canPrint && !isSaving,
                            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        ) {
                            BilingualLabel(R.string.share, R.string.share_ne, centered = true)
                        }
                    }
                    Button(
                        onClick = onPrint,
                        enabled = canPrint && !isSaving && !isPrinting,
                        modifier = Modifier.weight(1.25f).heightIn(min = 58.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = if (onShare == null) PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        else PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        if (onShare == null) {
                            BillIcon(BillIcon.Print, size = 21.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (isPrinting) {
                            Text(stringResource(R.string.thermal_sending))
                        } else {
                            BilingualLabel(
                                if (isSaving) R.string.saving_bill else R.string.print_bill,
                                if (isSaving) R.string.saving_bill_ne else R.string.print_bill_ne,
                                centered = onShare != null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BilingualLabel(
    @StringRes english: Int,
    @StringRes nepali: Int,
    modifier: Modifier = Modifier,
    mainStyle: TextStyle = MaterialTheme.typography.labelLarge,
    secondaryStyle: TextStyle = MaterialTheme.typography.labelSmall,
    centered: Boolean = false,
) {
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = stringResource(english),
            style = mainStyle,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
        )
        Text(text = stringResource(nepali), style = secondaryStyle, textAlign = textAlign)
    }
}

@Composable
internal fun EmptyScreen(
    @StringRes english: Int,
    @StringRes nepali: Int,
    icon: BillIcon,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.padding(16.dp)) { BillIcon(icon, size = 28.dp) }
            }
            BilingualLabel(english, nepali, centered = true, mainStyle = MaterialTheme.typography.bodyLarge)
        }
    }
}
