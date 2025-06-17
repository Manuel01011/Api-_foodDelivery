package com.example.proyectomoviles.backend.models

data class RestauranteReporte(
    val idRestaurante: Int,
    val nombreRestaurante: String,
    val tipoComida: String,
    val totalVendido: Double,
    val porcentajeTotal: Double,
    val ventasTotalesGenerales: Double
)