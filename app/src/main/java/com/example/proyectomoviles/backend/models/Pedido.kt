package com.example.proyectomoviles.backend.models

data class Pedido(
    val id_pedido: Int = 0,
    val id_cliente: Int = 0,
    val id_restaurante: Int = 0,
    val id_repartidor: Int? = null,
    val fecha_pedido: String = "",
    val estado: String = "pendiente", // "pendiente", "preparacion", "en_camino", "entregado", "cancelado"
    val subtotal: Double = 0.0,
    val costo_transporte: Double = 0.0,
    val iva: Double = 0.0,
    val total: Double = 0.0,
    val direccion_entrega: String = "",
    val detalles: List<DetallePedido> = emptyList()
)