package com.example.proyectomoviles.backend.models

data class Cliente(
    val id_usuario: Int,
    val cedula: String,
    val nombre: String,
    val correo: String,
    val direccion: String,
    val tipo : String,
    val telefono: String,
    val estado: String,
    val pedidos: List<Pedido> = emptyList()
)