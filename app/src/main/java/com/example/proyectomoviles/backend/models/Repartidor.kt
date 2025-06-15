package com.example.proyectomoviles.backend.models

data class Repartidor(
    val id_repartidor: Int,
    val id_usuario: Int,
    val nombre: String,
    val cedula: String,
    val estado_usuario: String,
    val estado_disponibilidad: String,
    val distancia_pedido: Double,
    val km_recorridos_diarios: Double,
    val costo_por_km: Double,
    val amonestaciones: Int
)