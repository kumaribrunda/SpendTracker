package com.spendtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { App() } } }
    }
}

private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val RED = Color(0xFFC62828)
private val GREEN = Color(0xFF2E7D32)
private fun ym(ts: Long) = YearMonth.from(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

@Composable
fun App() {
    val ctx = LocalContext.current
    val db = remember { Db(ctx) }
    var capital by remember { mutableStateOf(db.capital) }
    var refresh by remember { mutableIntStateOf(0) }
    val tick = Bus.tick
    val txns = remember(refresh, tick) { db.all() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showAdd by remember { mutableStateOf(false) }
    var editCap by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.RECEIVE_SMS) }

    val cap = capital
    if (cap == null) {
        AmountDialog("Set your initial capital", false, onDismiss = {}) { a, _ -> db.capital = a; capital = a }
        return
    }
    val signed = { t: Txn -> if (t.type == "CREDIT") t.amount else -t.amount }
    val opening = cap + txns.filter { ym(it.ts) < month }.sumOf(signed)
    val cur = txns.filter { ym(it.ts) == month }
    val spent = cur.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val added = cur.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val closing = opening + added - spent
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")

    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { showAdd = true }) { Text("+ Add credit") }
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            if (!granted) Text("SMS permission is needed to track debits automatically.", color = RED)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ month = month.minusMonths(1) }) { Text("◀") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), fontWeight = FontWeight.Bold)
                TextButton({ month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Text("▶") }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Balance at end of month")
                    Text(inr.format(closing), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Opening: ${inr.format(opening)}")
                    Row { Text("Spent: ${inr.format(spent)}", color = RED); Spacer(Modifier.width(16.dp)); Text("Credited: ${inr.format(added)}", color = GREEN) }
                    TextButton({ editCap = true }, contentPadding = PaddingValues(0.dp)) { Text("Initial capital: ${inr.format(cap)} (edit)") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (cur.isEmpty()) Text("No transactions this month.")
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(cur, key = { it.id }) { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.label.ifBlank { "Credit" }, maxLines = 1)
                            Text(Instant.ofEpochMilli(t.ts).atZone(ZoneId.systemDefault()).format(dateFmt), style = MaterialTheme.typography.bodySmall)
                        }
                        val credit = t.type == "CREDIT"
                        Text((if (credit) "+" else "-") + inr.format(t.amount), color = if (credit) GREEN else RED, fontWeight = FontWeight.Medium)
                        TextButton({ db.delete(t.id); refresh++ }) { Text("✕") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (showAdd) AmountDialog("Add credited amount", true, { showAdd = false }) { a, n ->
        if (a > 0) { db.insert("CREDIT", a, n.ifBlank { "Manual credit" }, System.currentTimeMillis()); refresh++ }
        showAdd = false
    }
    if (editCap) AmountDialog("Edit initial capital", false, { editCap = false }) { a, _ -> db.capital = a; capital = a; editCap = false }
}

@Composable
fun AmountDialog(title: String, note: Boolean, onDismiss: () -> Unit, onOk: (Double, String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(a, { a = it }, label = { Text("Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (note) OutlinedTextField(n, { n = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton({ a.toDoubleOrNull()?.takeIf { it >= 0 }?.let { onOk(it, n) } }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
