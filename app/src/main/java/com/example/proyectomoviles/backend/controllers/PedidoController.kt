package com.example.proyectomoviles.backend.controllers
import com.example.proyectomoviles.backend.models.ComboPedido
import com.example.proyectomoviles.backend.models.DetallePedido
import com.example.proyectomoviles.backend.models.Pedido
import com.example.proyectomoviles.backend.service.DatabaseDAO
import java.sql.SQLException
import java.sql.Types

class PedidoController {

    // Crear nuevo pedido
    fun crearPedido(idCliente: Int, idRestaurante: Int, direccionEntrega: String): Map<String, Int> {
        val procedureName = "sp_crear_pedido"
        val result = DatabaseDAO.executeStoredProcedureWithResults(
            procedureName,
            idCliente,
            idRestaurante,
            direccionEntrega
        )

        return result?.let {
            if (it.next()) {
                mapOf(
                    "id_pedido" to it.getInt("id_pedido"),
                    "id_repartidor" to it.getInt("id_repartidor")
                )
            } else {
                throw Exception("Error al crear pedido")
            }
        } ?: throw Exception("Error al crear pedido")
    }

    // Agregar combo a pedido
    fun agregarComboPedido(idPedido: Int, idCombo: Int, cantidad: Int): Int {
        val procedureName = "sp_agregar_combo_pedido"
        val result = DatabaseDAO.executeStoredProcedureWithResults(
            procedureName,
            idPedido,
            idCombo,
            cantidad
        )

        return result?.getInt("id_detalle") ?: throw Exception("Error al agregar combo al pedido")
    }

    // Finalizar pedido (calcular totales)
    fun finalizarPedido(idPedido: Int): Map<String, Double> {
        val procedureName = "sp_finalizar_pedido"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, idPedido)

        return result?.let {
            if (it.next()) {
                mapOf(
                    "subtotal" to it.getDouble("subtotal"),
                    "transporte" to it.getDouble("transporte"),
                    "iva" to it.getDouble("iva"),
                    "total" to it.getDouble("total")
                )
            } else {
                throw Exception("Error al finalizar pedido")
            }
        } ?: throw Exception("Error al finalizar pedido")
    }

    // Obtener pedidos por cliente
    fun obtenerPedidosCliente(idCliente: Int): List<Pedido> {
        val procedureName = "sp_obtener_pedidos_cliente"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, idCliente)

        val pedidos = mutableListOf<Pedido>()
        result?.let {
            while (it.next()) {
                pedidos.add(
                    Pedido(
                        id_pedido = it.getInt("id_pedido"),
                        id_cliente = idCliente,
                        id_restaurante = it.getInt("id_restaurante"),
                        id_repartidor = it.getInt("id_repartidor"),
                        fecha_pedido = it.getString("fecha_pedido"),
                        estado = it.getString("estado"),
                        subtotal = it.getDouble("subtotal"),
                        costo_transporte = it.getDouble("costo_transporte"),
                        iva = it.getDouble("iva"),
                        total = it.getDouble("total"),
                        direccion_entrega = it.getString("direccion_entrega")
                    )
                )
            }
        }
        return pedidos
    }

    // Obtener detalles de pedido
    fun obtenerDetallesPedido(idPedido: Int): List<DetallePedido> {
        val procedureName = "sp_obtener_detalles_pedido"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, idPedido)

        val detalles = mutableListOf<DetallePedido>()
        result?.let {
            while (it.next()) {
                detalles.add(
                    DetallePedido(
                        id_detalle = it.getInt("id_detalle"),
                        id_pedido = idPedido,
                        id_combo = it.getInt("id_combo"),
                        cantidad = it.getInt("cantidad"),
                        precio_unitario = it.getDouble("precio_unitario"),
                        nombre_combo = it.getString("nombre_combo")
                    )
                )
            }
        }
        return detalles
    }

    // funcion de pedidos
    fun crearPedidoCompleto(
        idCliente: Int,
        idRestaurante: Int,
        direccionEntrega: String,
        combos: List<ComboPedido>
    ): Map<String, Any> {
        println("[CONTROLLER] Iniciando creación de pedido...")
        println("[CONTROLLER] Cliente: $idCliente, Restaurante: $idRestaurante")
        println("[CONTROLLER] Combos recibidos: ${combos.joinToString { it.id_combo.toString() }}")

        val conn = DatabaseDAO.getConnection()
        try {

            // 3. Crear JSON para SP
            val combosJson = combos.joinToString(", ", "[", "]") { combo ->
                """{"id_combo": ${combo.id_combo}, "cantidad": ${combo.cantidad}}"""
            }
            println("[CONTROLLER] JSON para SP: $combosJson")

            val sql = "{call sp_crear_pedido_completo(?, ?, ?, ?)}"
            println("[CONTROLLER] Ejecutando SP: $sql")

            val cs = conn!!.prepareCall(sql).apply {
                setInt(1, idCliente)
                setInt(2, idRestaurante)
                setString(3, direccionEntrega)
                setString(4, combosJson)
            }

            val rs = cs.executeQuery()

            return if (rs.next()) {
                val resultado = mapOf(
                    "id_pedido" to rs.getInt("id_pedido"),
                    "id_repartidor" to rs.getInt("id_repartidor")
                )
                println("[CONTROLLER] Pedido creado exitosamente: $resultado")
                resultado
            } else {
                throw SQLException("No se pudo crear el pedido")
            }
        } catch (e: SQLException) {
            println("[CONTROLLER ERROR] SQLException: ${e.message}")
            throw e
        } finally {
            conn?.close()
        }
    }

    fun obtenerDetallePedidoCompleto(idPedido: Int, idCliente: Int): Map<String, Any> {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "{call sp_obtener_detalle_pedido_completo(?, ?)}"
            val cs = conn!!.prepareCall(sql)

            cs.setInt(1, idPedido)
            cs.setInt(2, idCliente)

            val rs = cs.executeQuery()

            if (rs.next()) {
                val detalles = mutableListOf<Map<String, Any>>()
                val detallesRs = cs.getResultSet()

                // Obtener detalles del pedido (segundo result set)
                while (detallesRs.next()) {
                    detalles.add(mapOf(
                        "id_detalle" to detallesRs.getInt("id_detalle"),
                        "id_combo" to detallesRs.getInt("id_combo"),
                        "nombre_combo" to detallesRs.getString("nombre_combo"),
                        "descripcion" to detallesRs.getString("descripcion"),
                        "cantidad" to detallesRs.getInt("cantidad"),
                        "precio_unitario" to detallesRs.getDouble("precio_unitario"),
                        "subtotal" to detallesRs.getDouble("subtotal")
                    ))
                }

                return mapOf(
                    "id_pedido" to rs.getInt("id_pedido"),
                    "fecha_pedido" to rs.getString("fecha_pedido"),
                    "estado" to rs.getString("estado"),
                    "subtotal" to rs.getDouble("subtotal"),
                    "costo_transporte" to rs.getDouble("costo_transporte"),
                    "iva" to rs.getDouble("iva"),
                    "total" to rs.getDouble("total"),
                    "direccion_entrega" to rs.getString("direccion_entrega"),
                    "restaurante" to rs.getString("restaurante"),
                    "direccion_restaurante" to rs.getString("direccion_restaurante"),
                    "repartidor" to mapOf(
                        "id" to rs.getInt("id_repartidor"),
                        "nombre" to rs.getString("nombre_repartidor"),
                        "telefono" to rs.getString("telefono_repartidor")
                    ),
                    "calificado" to (rs.getInt("calificado") == 1),
                    "detalles" to detalles
                )
            }
            throw SQLException("Pedido no encontrado o no pertenece al cliente")
        } finally {
            if (conn != null) {
                conn.close()
            }
        }
    }

    fun listarPedidosCliente(idCliente: Int, estado: String?): List<Map<String, Any>> {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = if (estado == null || estado.isEmpty()) {
                "{call sp_listar_pedidos_cliente(?, NULL)}"
            } else {
                "{call sp_listar_pedidos_cliente(?, ?)}"
            }

            val cs = conn!!.prepareCall(sql)

            cs.setInt(1, idCliente)
            if (estado != null && estado.isNotEmpty()) {
                cs.setString(2, estado)
            }

            val rs = cs.executeQuery()
            val pedidos = mutableListOf<Map<String, Any>>()

            while (rs.next()) {
                pedidos.add(mapOf(
                    "id_pedido" to rs.getInt("id_pedido"),
                    "fecha_pedido" to rs.getString("fecha_pedido"),
                    "estado" to rs.getString("estado"),
                    "total" to rs.getDouble("total"),
                    "restaurante" to rs.getString("restaurante"),
                    "tipo_comida" to rs.getString("tipo_comida"),
                    "repartidor" to mapOf(
                        "id" to rs.getInt("id_repartidor"),
                        "nombre" to rs.getString("nombre_repartidor")
                    ),
                    "calificado" to (rs.getInt("calificado") == 1)
                ))
            }
            return pedidos
        } finally {
            if (conn != null) {
                conn.close()
            }
        }
    }

    fun actualizarEstadoPedido(idPedido: Int, nuevoEstado: String): Boolean {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "{call sp_actualizar_estado_pedido(?, ?)}"
            val cs = conn!!.prepareCall(sql)

            cs.setInt(1, idPedido)
            cs.setString(2, nuevoEstado)

            val rs = cs.executeQuery()
            return rs.next() && rs.getInt("success") == 1
        } finally {
            if (conn != null) {
                conn.close()
            }
        }
    }

    fun calificarPedido(idPedido: Int, idCliente: Int, puntajeRepartidor: Int,
                        puntajeRestaurante: Int, comentario: String?, queja: Boolean): Boolean {
        println("=== INICIO DE CONTROLADOR ===")
        println("Parámetros recibidos: idPedido=$idPedido, idCliente=$idCliente, " +
                "puntajeRep=$puntajeRepartidor, puntajeRes=$puntajeRestaurante, " +
                "comentario=$comentario, queja=$queja")

        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "{call sp_calificar_pedido(?, ?, ?, ?, ?, ?)}"
            println("Preparando llamada a procedimiento: $sql")

            val cs = conn!!.prepareCall(sql)
            cs.setInt(1, idPedido)
            cs.setInt(2, idCliente)
            cs.setInt(3, puntajeRepartidor)
            cs.setInt(4, puntajeRestaurante)
            cs.setString(5, comentario)
            cs.setBoolean(6, queja)

            println("Parámetros establecidos en el CallableStatement")

            val rs = cs.executeQuery()
            val success = rs.next() && rs.getInt("success") == 1

            println("Resultado del procedimiento almacenado: success=$success")
            return success
        } catch (e: Exception) {
            println("Excepción en calificarPedido: ${e.message}")
            e.printStackTrace()
            return false
        } finally {
            if (conn != null) {
                conn.close()
                println("Conexión cerrada")
            }
            println("=== FIN DE CONTROLADOR ===")
        }
    }

    fun listarPedidosRestaurante(idRestaurante: Int, estado: String?): List<Map<String, Any>> {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "{call sp_listar_pedidos_restaurante(?, ?)}"
            val cs = conn!!.prepareCall(sql)

            cs.setInt(1, idRestaurante)
            if (estado != null) {
                cs.setString(2, estado)
            } else {
                cs.setNull(2, Types.VARCHAR)
            }

            val rs = cs.executeQuery()
            val pedidos = mutableListOf<Map<String, Any>>()

            while (rs.next()) {
                pedidos.add(mapOf(
                    "id_pedido" to rs.getInt("id_pedido"),
                    "fecha_pedido" to rs.getString("fecha_pedido"),
                    "estado" to rs.getString("estado"),
                    "total" to rs.getDouble("total"),
                    "nombre_cliente" to rs.getString("nombre_cliente"),
                    "direccion_cliente" to rs.getString("direccion_cliente"),
                    "nombre_repartidor" to rs.getString("nombre_repartidor"),
                    "cantidad_items" to rs.getInt("cantidad_items")
                ))
            }

            return pedidos
        } finally {
            conn?.close()
        }
    }

    fun verDetallePedidoRestaurante(idPedido: Int, idRestaurante: Int): Map<String, Any> {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "{call sp_ver_detalle_pedido_restaurante(?, ?)}"
            val cs = conn!!.prepareCall(sql)

            cs.setInt(1, idPedido)
            cs.setInt(2, idRestaurante)

            val rs = cs.executeQuery()
            val resultado = mutableMapOf<String, Any>()
            val detalles = mutableListOf<Map<String, Any>>()

            // Primera parte: información del pedido
            if (rs.next()) {
                resultado["id_pedido"] = rs.getInt("id_pedido")
                resultado["fecha_pedido"] = rs.getString("fecha_pedido")
                resultado["estado"] = rs.getString("estado")
                resultado["total"] = rs.getDouble("total")
                resultado["nombre_cliente"] = rs.getString("nombre_cliente")
                resultado["direccion_cliente"] = rs.getString("direccion_cliente")
                resultado["telefono_cliente"] = rs.getString("telefono")
                resultado["nombre_repartidor"] = rs.getString("nombre_repartidor")
                resultado["telefono_repartidor"] = rs.getString("telefono_repartidor")
            }

            // Segunda parte: detalles del pedido
            if (cs.moreResults) {
                val detallesRs = cs.resultSet
                while (detallesRs.next()) {
                    detalles.add(mapOf(
                        "combo" to detallesRs.getString("combo"),
                        "cantidad" to detallesRs.getInt("cantidad"),
                        "precio_unitario" to detallesRs.getDouble("precio_unitario"),
                        "subtotal" to detallesRs.getDouble("subtotal")
                    ))
                }
            }

            resultado["detalles"] = detalles
            return resultado
        } finally {
            conn?.close()
        }
    }

    fun actualizarEstadoPedidoRestaurante(idPedido: Int, nuevoEstado: String, idRestaurante: Int): Boolean {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "{call sp_actualizar_estado_pedido2(?, ?, ?)}"
            val cs = conn!!.prepareCall(sql)

            cs.setInt(1, idPedido)
            cs.setString(2, nuevoEstado)
            cs.setString(3, "restaurante") // Tipo de usuario

            val rs = cs.executeQuery()
            return rs.next() && rs.getInt("success") == 1
        } finally {
            conn?.close()
        }
    }

}