package com.example.proyectomoviles.backend.controllers

import com.example.banner.backend.service.GlobalException
import com.example.proyectomoviles.backend.models.Repartidor
import com.example.proyectomoviles.backend.service.DatabaseDAO
import java.sql.SQLException
import android.util.Log


class RepartidorController {

    // Registrar repartidor
    fun registrarRepartidor(idUsuario: Int, costoPorKm: Double): Int {
        val connection = DatabaseDAO.getConncection() ?: throw GlobalException("No se pudo obtener la conexión")
        try {
            // 1. Ejecutar el procedimiento sin esperar retorno
            val call = connection.prepareCall("{call sp_registrar_repartidor(?, ?)}")
            call.setInt(1, idUsuario)
            call.setDouble(2, costoPorKm)
            call.execute()
            call.close()

            // 2. Luego buscar el id_repartidor por id_usuario
            val ps = connection.prepareStatement("SELECT id_repartidor FROM Repartidores WHERE id_usuario = ?")
            ps.setInt(1, idUsuario)
            val rs = ps.executeQuery()
            if (rs.next()) {
                return rs.getInt("id_repartidor")
            } else {
                throw Exception("No se encontró el repartidor recién insertado.")
            }
        } finally {
            connection.close()
        }
    }

    // Actualizar estado de repartidor
    fun actualizarEstadoRepartidor(idRepartidor: Int, estado: String): Boolean {
        val procedureName = "sp_actualizar_estado_repartidor"
        return DatabaseDAO.executeStoredProcedure(procedureName, idRepartidor, estado)
    }

    // Asignar amonestación a repartidor
    fun asignarAmonestacion(idRepartidor: Int): Int {
        val procedureName = "sp_asignar_amonestacion"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, idRepartidor)

        return result?.getInt("amonestaciones_actuales")
            ?: throw Exception("Error al asignar amonestación")
    }

    // Obtener repartidores con cero amonestaciones
    fun obtenerRepartidoresCeroAmonestaciones(): List<Repartidor> {
        val procedureName = "getAllRepartidores"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName)

        val repartidores = mutableListOf<Repartidor>()
        result?.let {
            while (it.next()) {
                repartidores.add(
                    Repartidor(
                        id_repartidor = it.getInt("id_repartidor"),
                        id_usuario = it.getInt("id_usuario"),
                        nombre = it.getString("nombre"),
                        cedula = it.getString("cedula"),
                        estado_usuario = it.getString("estado_usuario"),
                        estado_disponibilidad = it.getString("estado_disponibilidad"),
                        distancia_pedido = it.getDouble("distancia_pedido"),
                        km_recorridos_diarios = it.getDouble("km_recorridos_diarios"),
                        costo_por_km = it.getDouble("costo_por_km"),
                        amonestaciones = it.getInt("amonestaciones"),
                    )
                )
            }
        }
        return repartidores
    }

    // Reporte de quejas por repartidor
    fun reporteQuejasRepartidores(): List<Map<String, Any>> {
        val procedureName = "sp_reporte_quejas_repartidores"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName)

        val reporte = mutableListOf<Map<String, Any>>()
        result?.let {
            while (it.next()) {
                reporte.add(
                    mapOf(
                        "id_repartidor" to it.getInt("id_repartidor"),
                        "nombre" to it.getString("nombre_repartidor"),
                        "total_quejas" to it.getInt("total_quejas")
                    )
                )
            }
        }
        return reporte
    }

}

