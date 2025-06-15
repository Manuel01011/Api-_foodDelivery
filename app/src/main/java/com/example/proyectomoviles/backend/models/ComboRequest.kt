package com.example.proyectomoviles.backend.models

data class ComboRequest(
    val id_restaurante: Int,
    val numero_combo: Int,
    val nombre: String,
    val descripcion: String
)