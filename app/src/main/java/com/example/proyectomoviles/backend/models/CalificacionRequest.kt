package com.example.proyectomoviles.backend.models

data class CalificacionRequest(
    val id_pedido: Int,
    val id_cliente: Int,
    val puntaje_repartidor: Int,
    val puntaje_restaurante: Int,
    val comentario: String?,
    val queja: Boolean
)