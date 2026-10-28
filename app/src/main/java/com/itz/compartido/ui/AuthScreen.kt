package com.itz.compartido.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itz.compartido.data.ItzRepository

@Composable
fun AuthScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ITZ-Compartido", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Viajes compartidos para la comunidad del Instituto Tecnológico de Zacatepec",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Iniciar sesión") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Registrarse") })
            }
            if (tab == 0) LoginForm() else RegistroForm()
        }
    }
}

@Composable
private fun LoginForm() {
    var correo by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Campo(correo, { correo = it }, "Correo institucional", KeyboardType.Email)
    Campo(pass, { pass = it }, "Contraseña", password = true)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        ItzRepository.login(correo, pass).onFailure { error = it.message }
    }, modifier = Modifier.fillMaxWidth()) { Text("Entrar") }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cuentas de demostración", fontWeight = FontWeight.Bold)
            Text("Pasajera: sofia${ItzRepository.DOMINIO_ITZ} / 123456")
            Text("Conductor: carlos${ItzRepository.DOMINIO_ITZ} / 123456")
            Text("Conductora sin validar: mariana${ItzRepository.DOMINIO_ITZ} / 123456")
            Text("Administrador: admin${ItzRepository.DOMINIO_ITZ} / admin123")
        }
    }
}

@Composable
private fun RegistroForm() {
    var nombre by rememberSaveable { mutableStateOf("") }
    var matricula by rememberSaveable { mutableStateOf("") }
    var correo by rememberSaveable { mutableStateOf("") }
    var tel by rememberSaveable { mutableStateOf("") }
    var contacto by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var acepto by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Campo(nombre, { nombre = it }, "Nombre completo")
    Campo(matricula, { matricula = it }, "Matrícula o número de empleado", KeyboardType.Number)
    Campo(correo, { correo = it }, "Correo institucional (${ItzRepository.DOMINIO_ITZ})", KeyboardType.Email)
    Campo(tel, { tel = it }, "Teléfono (10 dígitos)", KeyboardType.Phone)
    Campo(contacto, { contacto = it }, "Contacto de confianza para SOS (10 dígitos)", KeyboardType.Phone)
    Campo(pass, { pass = it }, "Contraseña (mínimo 6 caracteres)", password = true)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(acepto, { acepto = it })
        Text(
            "Acepto el aviso de privacidad y el deslinde de responsabilidad: la plataforma solo conecta " +
                "usuarios para compartir gastos de traslado, no presta servicio de transporte.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        if (!acepto) { error = "Debes aceptar el aviso de privacidad y el deslinde"; return@Button }
        ItzRepository.registrar(nombre, matricula, correo, tel, contacto, pass)
            .onFailure { error = it.message }
    }, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
}
