package com.example.proyectomoviles.backend.models

data class Usuario(
    val id_usuario: Int = 0,
    val cedula: String = "",
    val nombre: String = "",
    val correo: String = "",
    val direccion: String = "",
    val telefono: String = "",
    val numero_tarjeta: String = "",
    val tipo: String = "", // "cliente" o "repartidor"
    val estado: String = "activo" ,// "activo" o "suspendido"
    val origen: String = ""
)