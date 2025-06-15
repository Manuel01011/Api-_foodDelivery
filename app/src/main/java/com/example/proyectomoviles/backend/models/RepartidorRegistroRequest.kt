package com.example.proyectomoviles.backend.models

data class RepartidorRegistroRequest(
    val cedula: String,
    val nombre: String,
    val correo: String,
    val direccion: String,
    val telefono: String,
    val costo_por_km: Double
)