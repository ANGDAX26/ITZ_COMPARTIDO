package com.itz.compartido.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stages changes until SQLite commits, so screens never observe a failed write. */
internal class EstadoPersistente<T>(inicial: T) {
    private val publicado = MutableStateFlow(inicial)
    val flow: StateFlow<T> = publicado
    private var enTransaccion = false
    private var pendiente = inicial

    var value: T
        get() = if (enTransaccion) pendiente else publicado.value
        set(value) {
            if (enTransaccion) pendiente = value else publicado.value = value
        }

    fun update(transformar: (T) -> T) { value = transformar(value) }
    fun comenzar() { pendiente = publicado.value; enTransaccion = true }
    fun confirmar() { enTransaccion = false; publicado.value = pendiente }
    fun cancelar() { enTransaccion = false }
}
