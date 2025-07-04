package com.example.proyectomoviles.backend.controllers

import com.example.proyectomoviles.backend.models.Combo
import com.example.proyectomoviles.backend.models.ComboRequest
import com.example.proyectomoviles.backend.models.Restaurante
import com.example.proyectomoviles.backend.models.RestauranteReporte
import com.example.proyectomoviles.backend.service.DatabaseDAO

class RestauranteController {

    // Registrar nuevo restaurante
    fun registrarRestaurante(restaurante: Restaurante): Int {
        val procedureName = "sp_registrar_restaurante"
        val result = DatabaseDAO.executeStoredProcedureWithResults(
            procedureName,
            restaurante.nombre,
            restaurante.cedula_juridica,
            restaurante.direccion,
            restaurante.tipo_comida
        )

        if (result != null && result.next()) {
            return result.getInt("id_restaurante")
        } else {
            throw Exception("Error al registrar restaurante: ResultSet vacío")
        }
    }

    // Registrar combo para restaurante
    fun registrarCombo(combo: ComboRequest): Int {
        val procedureName = "sp_registrar_combo"
        val result = DatabaseDAO.executeStoredProcedureWithResults(
            procedureName,
            combo.id_restaurante,
            combo.numero_combo,
            combo.nombre,
            combo.descripcion
        )

        if (result != null && result.next()) {
            return result.getInt("id_combo")
        } else {
            throw Exception("Error al registrar combo o no se devolvió ningún ID.")
        }
    }

    // Obtener todos los restaurantes
    fun obtenerRestaurantes(): List<Restaurante> {
        val procedureName = "sp_obtener_restaurantes"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName)

        val restaurantes = mutableListOf<Restaurante>()
        result?.let {
            while (it.next()) {
                restaurantes.add(
                    Restaurante(
                        id_restaurante = it.getInt("id_restaurante"),
                        nombre = it.getString("nombre"),
                        cedula_juridica = it.getString("cedula_juridica"),
                        direccion = it.getString("direccion"),
                        tipo_comida = it.getString("tipo_comida")
                    )
                )
            }
        }
        return restaurantes
    }

    // Obtener combos de un restaurante
    fun obtenerCombos(idRestaurante: Int): List<Combo> {
        val procedureName = "sp_obtener_combos"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, idRestaurante)

        val combos = mutableListOf<Combo>()
        result?.let {
            while (it.next()) {
                combos.add(
                    Combo(
                        id_combo = it.getInt("id_combo"),
                        id_restaurante = idRestaurante,
                        numero_combo = it.getInt("numero_combo"),
                        nombre = it.getString("nombre"),
                        descripcion = it.getString("descripcion"),
                        precio = it.getDouble("precio")
                    )
                )
            }
        }
        return combos
    }

    // Reporte de restaurantes con mayor/menor pedidos
    fun reporteRestaurantesPedidos(tipo: String): Restaurante {
        val procedureName = "sp_reporte_restaurantes_pedidos"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, tipo)

        return result?.let {
            if (it.next()) {
                // Verificamos primero qué columnas están disponibles en el ResultSet
                val metaData = it.metaData
                val columnCount = metaData.columnCount
                var hasTotalPedidos = false

                for (i in 1..columnCount) {
                    if (metaData.getColumnLabel(i).equals("total_pedidos", ignoreCase = true)) {
                        hasTotalPedidos = true
                        break
                    }
                }

                Restaurante(
                    id_restaurante = it.getInt("id_restaurante"),
                    nombre = it.getString("nombre"),
                    total_pedidos = if (hasTotalPedidos) it.getInt("total_pedidos") else null
                )
            } else {
                throw Exception("No se encontraron restaurantes")
            }
        } ?: throw Exception("Error al generar reporte")
    }

    fun reporteVentasRestaurantes(): List<RestauranteReporte> {
        val procedureName = "sp_ventas_por_restaurante_simple"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName)

        val lista = mutableListOf<RestauranteReporte>()

        result?.let {
            while (it.next()) {
                val restaurante = RestauranteReporte(
                    idRestaurante = it.getInt("id_restaurante"),
                    nombreRestaurante = it.getString("nombre_restaurante"),
                    tipoComida = it.getString("tipo_comida"),
                    totalVendido = it.getDouble("total_vendido"),
                    porcentajeTotal = it.getDouble("porcentaje_total"),
                    ventasTotalesGenerales = it.getDouble("ventas_totales_generales")
                )
                lista.add(restaurante)
            }
            return lista
        } ?: throw Exception("Error al generar reporte")
    }

    // listar los restaurantes mas populares
    fun obtenerRestaurantesPopulares(): List<Map<String, Any>> {
        val restaurantes = mutableListOf<Map<String, Any>>()
        try {
            val connection = DatabaseDAO.getConnection()
            val query = """
            SELECT r.id_restaurante, r.nombre, r.tipo_comida, 
                   COUNT(p.id_pedido) AS total_pedidos
            FROM Restaurantes r
            LEFT JOIN Pedidos p ON r.id_restaurante = p.id_restaurante 
                AND p.estado != 'cancelado'
            WHERE r.activo = TRUE
            GROUP BY r.id_restaurante, r.nombre, r.tipo_comida
            ORDER BY total_pedidos DESC
        """

            val statement = connection!!.createStatement()
            val resultSet = statement.executeQuery(query)

            while (resultSet.next()) {
                val restaurante = mapOf(
                    "id_restaurante" to resultSet.getInt("id_restaurante"),
                    "nombre" to resultSet.getString("nombre"),
                    "tipo_comida" to resultSet.getString("tipo_comida"),
                    "total_pedidos" to resultSet.getInt("total_pedidos")
                )
                restaurantes.add(restaurante)
            }

            resultSet.close()
            statement.close()
            connection.close()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        return restaurantes
    }
}