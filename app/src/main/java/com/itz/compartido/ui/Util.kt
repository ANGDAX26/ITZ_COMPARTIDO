package com.itz.compartido.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.itz.compartido.data.ItzRepository
import com.itz.compartido.model.Usuario
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun usuarioActual(): Usuario? {
    val usuarios by ItzRepository.usuarios.collectAsState()
    val id by ItzRepository.sesionId.collectAsState()
    return usuarios.firstOrNull { it.idUsuario == id }
}

fun LocalTime.hhmm(): String = format(DateTimeFormatter.ofPattern("HH:mm"))
fun LocalDateTime.fechaHora(): String = format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
fun Double.mxn(): String = "$" + String.format(Locale.forLanguageTag("es-MX"), "%,.2f", this) + " MXN"
fun Usuario.estrellas(): String =
    if (numCalificaciones == 0) "Nuevo" else "★ " + String.format(Locale.US, "%.1f", calificacionProm) + " (" + numCalificaciones + ")"

@Composable
fun Campo(
    valor: String,
    onChange: (String) -> Unit,
    etiqueta: String,
    teclado: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else teclado),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun Etiqueta(texto: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer) {
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(texto, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Vacio(texto: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(texto, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
