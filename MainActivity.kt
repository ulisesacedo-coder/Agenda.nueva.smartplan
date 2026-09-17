package com.example.agendainteligente

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val Blue = Color(0xFF087FF5)
private val Orange = Color(0xFFFF9800)
private val Page = Color(0xFFF7F5FA)
private val TextDark = Color(0xFF151515)
private val TextMuted = Color(0xFF929197)

data class AgendaEvent(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val title: String,
    val time: String,
    val description: String,
    val color: String = "blue",
    val reminder: Boolean = false
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val date: String? = null
)

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("agenda_store", Context.MODE_PRIVATE)

    fun events(): List<AgendaEvent> = runCatching {
        val a = JSONArray(prefs.getString("events", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            AgendaEvent(
                id = o.optString("id"),
                date = o.optString("date"),
                title = o.optString("title"),
                time = o.optString("time"),
                description = o.optString("description"),
                color = o.optString("color", "blue"),
                reminder = o.optBoolean("reminder")
            )
        }
    }.getOrDefault(emptyList())

    fun saveEvents(items: List<AgendaEvent>) {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("title", it.title)
                put("time", it.time); put("description", it.description)
                put("color", it.color); put("reminder", it.reminder)
            })
        }
        prefs.edit().putString("events", a.toString()).apply()
    }

    fun notes(): List<Note> = runCatching {
        val a = JSONArray(prefs.getString("notes", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Note(o.optString("id"), o.optString("title"), o.optString("body"),
                o.optBoolean("pinned"), o.optString("date").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    fun saveNotes(items: List<Note>) {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("title", it.title); put("body", it.body)
                put("pinned", it.pinned); put("date", it.date ?: "")
            })
        }
        prefs.edit().putString("notes", a.toString()).apply()
    }

    fun dark(): Boolean = prefs.getBoolean("dark", false)
    fun setDark(value: Boolean) = prefs.edit().putBoolean("dark", value).apply()
}

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        createNotificationChannel()
        setContent { AgendaApp(store) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("agenda", "Agenda", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
}

@Composable
fun AgendaApp(store: LocalStore) {
    var dark by remember { mutableStateOf(store.dark()) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var events by remember {
        mutableStateOf(
            store.events().ifEmpty {
                listOf(
                    AgendaEvent(date = "2026-09-17", title = "Reunión de equipo", time = "09:00 – 10:00",
                        description = "Repasar avances de la semana y pendientes del proyecto."),
                    AgendaEvent(date = "2026-09-17", title = "Ideas para el proyecto", time = "",
                        description = "Propuesta para el cliente: enfocarse en la reducción de costos y los tiempos de entrega.",
                        color = "orange")
                )
            }
        )
    }
    var notes by remember {
        mutableStateOf(
            store.notes().ifEmpty {
                listOf(
                    Note(title = "Lista de compras", body = "Café de grano\nLeche\nHuevos...", pinned = true),
                    Note(title = "Lista de compras", body = "Café de grano\nLeche\nHuevos...", pinned = true),
                    Note(title = "Ideas para el proyecto",
                        body = "Propuesta para el cliente: enfocarse en la reducción de costos y los tiempos de entrega.\nPreparar presentación para el viernes.",
                        date = "17 sep 2026"),
                    Note(title = "Pendientes de casa",
                        body = "Llamar al plomero, cambiar el filtro del aire y regar las plantas este fin de semana.")
                )
            }
        }
    }

    var showEvent by remember { mutableStateOf<AgendaEvent?>(null) }
    var showNote by remember { mutableStateOf<Note?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val scheme = if (dark) darkColorScheme(primary = Blue) else lightColorScheme(primary = Blue)
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = if (dark) Color(0xFF121212) else Page,
            bottomBar = { BottomNavigationBar(tab) { tab = it } }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Header(onSettings = { showSettings = true })
                WorkspaceSelector()
                if (tab == 0) {
                    CalendarScreen(
                        month, selectedDate, events,
                        onPrevious = { month = month.minusMonths(1) },
                        onNext = { month = month.plusMonths(1) },
                        onDateSelected = { selectedDate = it },
                        onNew = { showEvent = AgendaEvent(date = selectedDate.toString(), title = "", time = "09:00 – 10:00", description = "") },
                        onEdit = { showEvent = it },
                        onDelete = {
                            events = events.filterNot { e -> e.id == it.id }
                            store.saveEvents(events)
                        }
                    )
                } else {
                    NotesScreen(
                        notes,
                        onNew = { showNote = Note(title = "", body = "") },
                        onEdit = { showNote = it },
                        onDelete = {
                            notes = notes.filterNot { n -> n.id == it.id }
                            store.saveNotes(notes)
                        },
                        onPin = {
                            notes = notes.map { n -> if (n.id == it.id) n.copy(pinned = !n.pinned) else n }
                            store.saveNotes(notes)
                        }
                    )
                }
            }
        }
    }

    showEvent?.let { original ->
        EventDialog(original, onDismiss = { showEvent = null }) { edited ->
            events = (events.filterNot { it.id == edited.id } + edited).sortedBy { it.time }
            store.saveEvents(events)
            showEvent = null
        }
    }

    showNote?.let { original ->
        NoteDialog(original, onDismiss = { showNote = null }) { edited ->
            notes = (notes.filterNot { it.id == edited.id } + edited)
                .sortedWith(compareByDescending<Note> { it.pinned })
            store.saveNotes(notes)
            showNote = null
        }
    }

    if (showSettings) {
        SettingsDialog(
            dark = dark,
            onDark = {
                dark = it
                store.setDark(it)
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Orange))
        Spacer(Modifier.width(16.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE9E7E2)) {
            Text("Avance", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 14.sp)
        }
        Text("Panel", Modifier.padding(horizontal = 10.dp), fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configuración") }
        Surface(shape = RoundedCornerShape(7.dp), color = Color(0xFF111111), modifier = Modifier.size(38.dp)) {
            IconButton(onClick = {}) { Icon(Icons.Default.RocketLaunch, null, tint = Color.White) }
        }
    }
}

@Composable
fun WorkspaceSelector() {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sync, null, tint = Color.DarkGray, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(14.dp))
            Text("Hogar", fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.Gray)
        }
    }
}

@Composable
fun CalendarScreen(
    month: YearMonth, selectedDate: LocalDate, events: List<AgendaEvent>,
    onPrevious: () -> Unit, onNext: () -> Unit, onDateSelected: (LocalDate) -> Unit,
    onNew: () -> Unit, onEdit: (AgendaEvent) -> Unit, onDelete: (AgendaEvent) -> Unit
) {
    val monthName = month.month.getDisplayName(TextStyle.FULL, Locale("es", "MX")).replaceFirstChar { it.uppercase() }
    val dayEvents = events.filter { it.date == selectedDate.toString() }.sortedBy { it.time }

    Column(Modifier.fillMaxSize()) {
        Text("MI AGENDA", Modifier.padding(start = 22.dp, top = 45.dp), fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Text("Calendario", Modifier.padding(start = 22.dp, top = 4.dp), fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(26.dp))

        Surface(Modifier.padding(horizontal = 22.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 25.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, null, tint = Blue, modifier = Modifier.size(30.dp)) }
                    Text("$monthName ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, null, tint = Blue, modifier = Modifier.size(30.dp)) }
                }
                CalendarGrid(month, selectedDate, events, onDateSelected)
            }
        }

        Spacer(Modifier.height(22.dp))
        Button(onClick = onNew, Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(68.dp),
            shape = RoundedCornerShape(34.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
            Icon(Icons.Default.Add, null, Modifier.size(28.dp))
            Spacer(Modifier.width(9.dp))
            Text("Nuevo evento", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX")))
                .replaceFirstChar { it.uppercase() },
            Modifier.padding(start = 43.dp, top = 32.dp, bottom = 12.dp),
            fontSize = 16.sp, color = TextMuted, fontWeight = FontWeight.Medium
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (dayEvents.isEmpty()) {
                item { Text("No hay eventos para este día.", Modifier.padding(horizontal = 43.dp, vertical = 18.dp), color = TextMuted) }
            }
            items(dayEvents, key = { it.id }) { event ->
                EventCard(event, onEdit, onDelete)
            }
        }
    }
}

@Composable
fun CalendarGrid(month: YearMonth, selectedDate: LocalDate, events: List<AgendaEvent>, onDateSelected: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val start = (first.dayOfWeek.value % 7)
    val headers = listOf("D","L","M","X","J","V","S")
    var day = 1

    Column {
        Row(Modifier.fillMaxWidth()) {
            headers.forEach { h -> Text(h, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = TextMuted) }
        }
        Spacer(Modifier.height(10.dp))
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth().height(62.dp)) {
                repeat(7) { col ->
                    val index = week * 7 + col
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (index >= start && day <= month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val selected = date == selectedDate
                            val hasEvent = events.any { it.date == date.toString() }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(if (selected) Blue else Color.Transparent)
                                    .clickable { onDateSelected(date) }, contentAlignment = Alignment.Center) {
                                    Text(day.toString(), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                }
                                if (hasEvent && !selected) Box(Modifier.size(5.dp).clip(CircleShape).background(Blue))
                            }
                            day++
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(event: AgendaEvent, onEdit: (AgendaEvent) -> Unit, onDelete: (AgendaEvent) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 43.dp, vertical = 5.dp).clickable { onEdit(event) },
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(7.dp).fillMaxHeight().background(if (event.color == "orange") Orange else Blue))
            Column(Modifier.padding(16.dp).weight(1f)) {
                if (event.time.isNotBlank()) Text(event.time, color = TextMuted, fontSize = 15.sp)
                Text(event.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(event.description, color = TextMuted, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    Modifier.padding(top = 7.dp))
                if (event.reminder) Text("🔔 Recordatorio activado", color = Blue, fontSize = 12.sp, Modifier.padding(top = 5.dp))
            }
            IconButton(onClick = { onDelete(event) }) { Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF5B58)) }
        }
    }
}

@Composable
fun NotesScreen(notes: List<Note>, onNew: () -> Unit, onEdit: (Note) -> Unit, onDelete: (Note) -> Unit, onPin: (Note) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = notes.filter { it.title.contains(query, true) || it.body.contains(query, true) }

    Column(Modifier.fillMaxSize()) {
        Text("NOTAS", Modifier.padding(start = 22.dp, top = 45.dp), fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Text("Notas", Modifier.padding(start = 22.dp, top = 4.dp), fontSize = 38.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Buscar notas", color = Color(0xFFB5B3B8), fontSize = 18.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp).height(58.dp),
            shape = RoundedCornerShape(30.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = Color.Transparent),
            singleLine = true
        )

        Button(onClick = onNew, Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(65.dp),
            shape = RoundedCornerShape(33.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
            Icon(Icons.Default.Add, null, Modifier.size(27.dp)); Spacer(Modifier.width(9.dp))
            Text("Nueva nota", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Text("NOTAS", Modifier.padding(start = 26.dp, top = 25.dp, bottom = 8.dp), fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Medium)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(filtered, key = { it.id }) { note -> NoteCard(note, onEdit, onDelete, onPin) }
        }
    }
}

@Composable
fun NoteCard(note: Note, onEdit: (Note) -> Unit, onDelete: (Note) -> Unit, onPin: (Note) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp).clickable { onEdit(note) },
        shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                IconButton(onClick = { onPin(note) }) {
                    Icon(if (note.pinned) Icons.Default.PushPin else Icons.Default.PushPin,
                        "Fijar", tint = if (note.pinned) Orange else TextMuted)
                }
                IconButton(onClick = { onDelete(note) }) { Icon(Icons.Default.DeleteOutline, "Eliminar", tint = Color(0xFFFF5B58)) }
            }
            Text(note.body.replace("
", "\n- "), Modifier.padding(top = 2.dp), color = TextMuted, fontSize = 17.sp,
                lineHeight = 25.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            note.date?.let {
                Surface(Modifier.padding(top = 11.dp), shape = RoundedCornerShape(20.dp), color = Color(0xFFE6F1FF)) {
                    Text(it, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Blue, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(selected == 0, { onSelect(0) },
            icon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(29.dp)) },
            label = { Text("Calendario", fontSize = 13.sp) })
        NavigationBarItem(selected == 1, { onSelect(1) },
            icon = { Icon(Icons.Default.Description, null, Modifier.size(29.dp)) },
            label = { Text("Notas", fontSize = 13.sp) })
    }
}

@Composable
fun EventDialog(original: AgendaEvent, onDismiss: () -> Unit, onSave: (AgendaEvent) -> Unit) {
    var title by remember { mutableStateOf(original.title) }
    var time by remember { mutableStateOf(original.time) }
    var date by remember { mutableStateOf(original.date) }
    var desc by remember { mutableStateOf(original.description) }
    var reminder by remember { mutableStateOf(original.reminder) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original.title.isBlank()) "Nuevo evento" else "Editar evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Horario") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción") }, minLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reminder, { reminder = it })
                    Text("Recordatorio")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess)
                    onSave(original.copy(title = title, date = date, time = time, description = desc, reminder = reminder))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun NoteDialog(original: Note, onDismiss: () -> Unit, onSave: (Note) -> Unit) {
    var title by remember { mutableStateOf(original.title) }
    var body by remember { mutableStateOf(original.body) }
    var pinned by remember { mutableStateOf(original.pinned) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original.title.isBlank()) "Nueva nota" else "Editar nota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(body, { body = it }, label = { Text("Contenido") }, minLines = 5)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(pinned, { pinned = it }); Text("Fijar nota")
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onSave(original.copy(title = title, body = body, pinned = pinned)) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun SettingsDialog(dark: Boolean, onDark: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Configuración") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Modo oscuro", Modifier.weight(1f))
                    Switch(dark, onDark)
                }
                Text("Los eventos y notas se guardan localmente en el dispositivo.",
                    color = TextMuted, fontSize = 13.sp, Modifier.padding(top = 15.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
