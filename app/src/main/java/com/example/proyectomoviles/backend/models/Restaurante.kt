package com.example.proyectomoviles.backend.models

data class Restaurante(
    val id_restaurante: Int = 0,
    val nombre: String = "",
    val cedula_juridica: String = "",
    val direccion: String = "",
    val tipo_comida: String = "", // "china", "rapida", "saludable", etc.
    val combos: List<Combo> = emptyList(),
    val activo: Boolean = true,
    val total_pedidos: Int? = null // Hacerlo nullable
)