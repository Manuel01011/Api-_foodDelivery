package com.example.proyectomoviles.backend.models

data class Combo(
    val id_combo: Int = 0,
    val id_restaurante: Int = 0,
    val numero_combo: Int = 0,
    val nombre: String = "",
    val descripcion: String = "",
    val precio: Double = 0.0,
    val activo: Boolean = true
)