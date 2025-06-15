package com.example.proyectomoviles.backend.models

data class DetallePedido(
    val id_detalle: Int = 0,
    val id_pedido: Int = 0,
    val id_combo: Int = 0,
    val cantidad: Int = 1,
    val precio_unitario: Double = 0.0,
    val nombre_combo: String = "" // Para mostrar en la UI
)