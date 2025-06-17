package com.example.proyectomoviles.backend

import com.example.proyectomoviles.backend.controllers.PedidoController
import com.example.proyectomoviles.backend.controllers.RepartidorController
import com.example.proyectomoviles.backend.controllers.RestauranteController
import com.example.proyectomoviles.backend.controllers.UsuarioController
import com.example.proyectomoviles.backend.models.CalificacionRequest
import com.example.proyectomoviles.backend.models.ComboPedido
import com.example.proyectomoviles.backend.models.ComboRequest
import com.example.proyectomoviles.backend.models.RepartidorRegistroRequest
import com.example.proyectomoviles.backend.models.Restaurante
import com.example.proyectomoviles.backend.models.Usuario
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class FoodHttpServer(private val port: Int) {
    private val gson = Gson()
    private val usuarioController = UsuarioController()
    private val restauranteController = RestauranteController()
    private val pedidoController = PedidoController()
    private val repartidorController = RepartidorController()

    fun start() {
        Thread {
            val serverSocket = ServerSocket(port)
            println("Servidor de comida iniciado en puerto $port")

            while (true) {
                try {
                    val clientSocket = serverSocket.accept()
                    handleRequest(clientSocket)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun handleRequest(clientSocket: Socket) {
        var reader: BufferedReader? = null
        var writer: PrintWriter? = null

        try {
            reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            writer = PrintWriter(clientSocket.getOutputStream(), true)

            // Leer primera línea (request line)
            val requestLine = reader.readLine() ?: run {
                sendErrorResponse(writer, "Petición inválida", 400)
                return
            }

            val requestParts = requestLine.split(" ")
            if (requestParts.size < 2) {
                sendErrorResponse(writer, "Petición inválida", 400)
                return
            }

            val method = requestParts[0]
            val path = requestParts[1]

            // Leer headers para obtener Content-Length si existe
            var contentLength = 0
            while (true) {
                val headerLine = reader.readLine() ?: break
                if (headerLine.isEmpty()) break // Fin de headers

                if (headerLine.startsWith("Content-Length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // Leer el cuerpo solo si hay contenido
            val body = if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                reader.read(buffer, 0, contentLength)
                String(buffer)
            } else {
                ""
            }

            when {
                method == "OPTIONS" -> handleOptionsRequest(writer)

                // Rutas para Usuarios/Clientes
                path.equals("/api/usuarios", ignoreCase = true) && method == "POST" -> {
                    handleRegistrarUsuario(writer, body)
                }
                path.startsWith("/api/usuarios/") && method == "GET" -> {
                    val cedula = path.removePrefix("/api/usuarios/")
                    handleVerificarUsuario(writer, cedula)
                }
                path.equals("/api/clientes", ignoreCase = true) && method == "GET" -> {
                    handleObtenerClientes(writer)
                }
                path.equals("/api/reportes/clientes-pedidos", ignoreCase = true) && method == "GET" -> {
                    handleReporteClientesYPedidos(writer)
                }

                // Rutas para Restaurantes
                path.equals("/api/reportes/restaurantes-populares", ignoreCase = true) && method == "GET" -> {
                    handleReporteRestaurantesPopulares(writer)
                }
                path.equals("/api/reportes/calificaciones-repartidores", ignoreCase = true) && method == "GET" -> {
                    handleReporteCalificacionesRepartidores(writer)
                }
                path.equals("/api/restaurantes", ignoreCase = true) && method == "GET" -> {
                    handleObtenerRestaurantes(writer)
                }
                path.equals("/api/restaurantes", ignoreCase = true) && method == "POST" -> {
                    handleRegistrarRestaurante(writer, body)
                }
                path.startsWith("/api/restaurantes/") && path.endsWith("/combos") && method == "GET" -> {
                    val idRestaurante = path.removePrefix("/api/restaurantes/").removeSuffix("/combos").toIntOrNull()
                    handleObtenerCombos(writer, idRestaurante)
                }
                path.startsWith("/api/restaurantes/") && path.contains("/pedidos") && method == "GET" -> {
                    val basePath = path.split('?')[0]
                    val idRestaurante = basePath.removePrefix("/api/restaurantes/").removeSuffix("/pedidos").toIntOrNull()
                    val estado = getQueryParam(path, "estado")
                    handleObtenerPedidosRestaurante(writer, idRestaurante, estado)
                }

                path.startsWith("/api/restaurantes/") && path.contains("/pedidos/") && method == "GET" -> {
                    val parts = path.removePrefix("/api/restaurantes/").split("/pedidos/")
                    val idRestaurante = parts[0].toIntOrNull()
                    val idPedido = parts[1].toIntOrNull()
                    handleVerDetallePedidoRestaurante(writer, idPedido, idRestaurante)
                }

                path.startsWith("/api/restaurantes/") && path.contains("/pedidos/") && path.endsWith("/estado") && method == "PUT" -> {
                    val parts = path.removePrefix("/api/restaurantes/").split("/pedidos/")
                    val idRestaurante = parts[0].toIntOrNull()
                    val idPedido = parts[1].removeSuffix("/estado").toIntOrNull()
                    handleActualizarEstadoPedidoRestaurante(writer, idPedido, body)
                }


                // Rutas para Pedidos
                path.equals("/api/pedidos", ignoreCase = true) && method == "POST" -> {
                    handleCrearPedido(writer, body)
                }
                path.startsWith("/api/pedidos/") && path.endsWith("/combos") && method == "POST" -> {
                    val idPedido = path.removePrefix("/api/pedidos/").removeSuffix("/combos").toIntOrNull()
                    handleAgregarComboPedido(writer, idPedido, body)
                }
                path.startsWith("/api/pedidos/") && path.endsWith("/finalizar") && method == "POST" -> {
                    val idPedido = path.removePrefix("/api/pedidos/").removeSuffix("/finalizar").toIntOrNull()
                    handleFinalizarPedido(writer, idPedido)
                }
                path.startsWith("/api/clientes/") && path.endsWith("/pedidos") && method == "GET" -> {
                    val idCliente = path.removePrefix("/api/clientes/").removeSuffix("/pedidos").toIntOrNull()
                    handleObtenerPedidosCliente(writer, idCliente)
                }

                // Rutas para Repartidores
                path.equals("/api/repartidores", ignoreCase = true) && method == "GET" -> {
                    handleObtenerRepartidores(writer)
                }
                path.equals("/api/repartidores/cero-amorestaciones", ignoreCase = true) && method == "GET" -> {
                    handleRepartidoresCeroAmonestaciones(writer)
                }
                path.startsWith("/api/repartidores/") && path.endsWith("/amonestacion") && method == "POST" -> {
                    val idRepartidor = path.removePrefix("/api/repartidores/").removeSuffix("/amonestacion").toIntOrNull()
                    handleAsignarAmonestacion(writer, idRepartidor)
                }
                path.equals("/api/repartidores", ignoreCase = true) && method == "POST" -> {
                    handleRegistrarRepartidor(writer, body)
                }
                path.startsWith("/api/repartidores/") && path.contains("/pedidos") && method == "GET" -> {
                    val basePath = path.split('?')[0]
                    val idRepartidor = basePath.removePrefix("/api/repartidores/").removeSuffix("/pedidos").toIntOrNull()
                    val estado = getQueryParam(path, "estado")
                    handleObtenerPedidosRepartidor(writer, idRepartidor, estado)
                }

                // Rutas para Reportes
                path.equals("/api/reportes/ventas-restaurantes", ignoreCase = true) && method == "GET" -> {
                    handleReporteVentasRestaurantes(writer)
                }
                path.equals("/api/reportes/quejas-repartidores", ignoreCase = true) && method == "GET" -> {
                    handleReporteQuejasRepartidores(writer)
                }

                //Rutas para combos
                path.startsWith("/api/restaurantes/") && path.endsWith("/combos") && method == "POST" -> {
                    handleRegistrarCombo(writer, body)
                }

                //Ruta para realizar pedidos
                path.equals("/api/pedidos/completo", ignoreCase = true) && method == "POST" -> {
                    handleCrearPedidoCompleto(writer, body)
                }

                path.startsWith("/api/pedidos/") && path.endsWith("/detalle") && method == "GET" -> {
                    val idPedido = path.removePrefix("/api/pedidos/").removeSuffix("/detalle").toIntOrNull()
                    val idCliente = getQueryParam(path, "cliente")?.toIntOrNull()
                    handleObtenerDetallePedido(writer, idPedido, idCliente)
                }

                path.startsWith("") && path.endsWith("/pedidos-completos") && method == "GET" -> {
                    val idCliente = path.removePrefix("/api/clientes/").removeSuffix("/pedidos-completos").toIntOrNull()
                    val estado = getQueryParam(path, "estado")
                    handleListarPedidosCliente(writer, idCliente, estado)
                }

                path.startsWith("/api/pedidos/") && path.endsWith("/estado") && method == "PUT" -> {
                    val idPedido = path.removePrefix("/api/pedidos/").removeSuffix("/estado").toIntOrNull()
                    handleActualizarEstadoPedido(writer, idPedido, body)
                }

                path.equals("/api/pedidos/calificar", ignoreCase = true) && method == "POST" -> {
                    handleCalificarPedido(writer, body)
                }

                else -> {
                    sendErrorResponse(writer, "Ruta no encontrada", 404)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            writer?.println("HTTP/1.1 500 Internal Server Error")
            writer?.println()
        } finally {
            reader?.close()
            writer?.close()
            clientSocket.close()
        }
    }

    // ------------------------- Handlers para Usuarios/Clientes -------------------------
    private fun handleRegistrarUsuario(writer: PrintWriter, body: String) {
        try {
            val usuario = gson.fromJson(body, Usuario::class.java)
            val idUsuario = usuarioController.registrarUsuario(usuario)
            sendJsonResponse(writer, mapOf("success" to true, "id_usuario" to idUsuario))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al registrar usuario: ${e.message}")
        }
    }

    private fun handleVerificarUsuario(writer: PrintWriter, cedula: String) {
        try {
            println("=== INICIO DE VERIFICACIÓN ===") // Log en consola del servidor
            println("Cédula recibida: $cedula")

            val usuario = usuarioController.verificarUsuario(cedula)
            println("Usuario obtenido: $usuario") // Ver objeto completo

            if (usuario != null) {
                println("Detalles usuario: id=${usuario.id_usuario}, nombre=${usuario.nombre}, tipo=${usuario.tipo}, origen=${usuario.origen}")

                val response = mapOf<String, Any>(
                    "id_usuario" to usuario.id_usuario,
                    "nombre" to usuario.nombre,
                    "tipo" to usuario.tipo,
                    "estado" to (usuario.estado ?: ""), // asegúrate de no enviar null
                    "origen" to (usuario.origen ?: ""),
                    "cedula" to usuario.cedula
                )

                println("Respuesta JSON que se enviará: $response")
                sendJsonResponse(writer, response)
            } else {
                println("Usuario no encontrado para cédula: $cedula")
                sendErrorResponse(writer, "Usuario no encontrado", 404)
            }
        } catch (e: Exception) {
            println("ERROR en verificación: ${e.message}")
            e.printStackTrace()
            sendErrorResponse(writer, "Error al verificar usuario", 500)
        }
    }

    private fun handleObtenerClientes(writer: PrintWriter) {
        try {
            val clientes = usuarioController.listarClientes("activo")
            sendJsonResponse(writer, mapOf("success" to true, "clientes" to clientes))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener clientes: ${e.message}")
        }
    }

    private fun handleReporteClientesYPedidos(writer: PrintWriter) {
        try {
            val resultado = pedidoController.obtenerClientesYPedidos()

            val pedidos = resultado["pedidos"] as? List<Map<String, Any>> ?: emptyList()
            val top = resultado["top"] as? Map<String, Any> ?: emptyMap()

            sendJsonResponse(writer, mapOf(
                "success" to true,
                "pedidos_por_cliente" to pedidos,
                "cliente_top" to top,
                "fecha_reporte" to System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener reporte: ${e.message}")
        }
    }


    // ------------------------- Handlers para Restaurantes -------------------------
    private fun handleObtenerRestaurantes(writer: PrintWriter) {
        try {
            val restaurantes = restauranteController.obtenerRestaurantes()
            sendJsonResponse(writer, mapOf("success" to true, "restaurantes" to restaurantes))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener restaurantes: ${e.message}")
        }
    }

    private fun handleReporteRestaurantesPopulares(writer: PrintWriter) {
        try {
            val restaurantes = restauranteController.obtenerRestaurantesPopulares()

            // Agregar ranking
            val rankedRestaurantes = restaurantes.mapIndexed { index, restaurante ->
                restaurante + ("ranking" to (index + 1))
            }

            sendJsonResponse(writer, mapOf(
                "success" to true,
                "restaurantes" to rankedRestaurantes,
                "total_restaurantes" to rankedRestaurantes.size,
                "fecha_reporte" to System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al generar reporte: ${e.message}")
        }
    }

    private fun handleReporteCalificacionesRepartidores(writer: PrintWriter) {
        try {
            val calificaciones = repartidorController.obtenerCalificacionesRepartidores()

            sendJsonResponse(writer, mapOf(
                "success" to true,
                "calificaciones" to calificaciones,
                "total_calificaciones" to calificaciones.size,
                "fecha_reporte" to System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener calificaciones: ${e.message}")
        }
    }

    private fun handleRegistrarRestaurante(writer: PrintWriter, body: String) {
        try {
            println("JSON recibido:\n$body")

            val restaurante = gson.fromJson(body, Restaurante::class.java)
            val idRestaurante = restauranteController.registrarRestaurante(restaurante)
            println("Restaurante registrado con ID: $idRestaurante") // Debug
            sendJsonResponse(writer, mapOf("success" to true, "id_restaurante" to idRestaurante))
            println("Respuesta enviada correctamente") // Debug
        } catch (e: Exception) {
            println("Excepción capturada: ${e.message}") // Debug
            sendErrorResponse(writer, "Error al registrar restaurante: ${e.message}")
        }
    }

    private fun handleObtenerCombos(writer: PrintWriter, idRestaurante: Int?) {
        try {
            if (idRestaurante == null) {
                sendErrorResponse(writer, "ID de restaurante inválido")
                return
            }
            val combos = restauranteController.obtenerCombos(idRestaurante)
            sendJsonResponse(writer, mapOf("success" to true, "combos" to combos))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener combos: ${e.message}")
        }
    }

    private fun handleObtenerPedidosRestaurante(writer: PrintWriter, idRestaurante: Int?, estado: String?) {
        try {
            if (idRestaurante == null) {
                sendErrorResponse(writer, "ID de restaurante inválido")
                return
            }

            val pedidos = pedidoController.listarPedidosRestaurante(idRestaurante, estado)
            sendJsonResponse(writer, mapOf("success" to true, "pedidos" to pedidos))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener pedidos del restaurante: ${e.message}")
        }
    }

    private fun handleVerDetallePedidoRestaurante(writer: PrintWriter, idPedido: Int?, idRestaurante: Int?) {
        try {
            if (idPedido == null || idRestaurante == null) {
                sendErrorResponse(writer, "Parámetros inválidos")
                return
            }

            val detalle = pedidoController.verDetallePedidoRestaurante(idPedido, idRestaurante)
            sendJsonResponse(writer, mapOf("success" to true, "pedido" to detalle))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener detalle del pedido: ${e.message}")
        }
    }

    private fun handleActualizarEstadoPedidoRestaurante(writer: PrintWriter, idPedido: Int?, body: String) {
        try {
            if (idPedido == null) {
                sendErrorResponse(writer, "ID de pedido inválido", 400)
                return
            }
            val json = JsonParser.parseString(body).asJsonObject

            // Restaurantes solo pueden cambiar a 'en_camino'
            val nuevoEstado = "en_camino" // Fijo para restaurantes

            val resultado = pedidoController.actualizarEstadoPedido(idPedido, nuevoEstado)

            if (resultado) {
                sendJsonResponse(writer, mapOf(
                    "success" to true,
                    "message" to "Estado actualizado a $nuevoEstado"
                ))
            } else {
                sendErrorResponse(writer, "Error al actualizar estado del pedido", 500)
            }
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error: ${e.message}", 500)
        }
    }

    // ------------------------- Handlers para Pedidos -------------------------
    private fun handleCrearPedido(writer: PrintWriter, body: String) {
        try {
            println("[DEBUG] Body recibido: $body") // 1. Verificar body completo

            val json = JsonParser.parseString(body).asJsonObject
            val idCliente = json.get("id_cliente").asInt
            val idRestaurante = json.get("id_restaurante").asInt
            val direccionEntrega = json.get("direccion_entrega").asString
            val combosJson = json.getAsJsonArray("combos")

            println("[DEBUG] Datos parseados: cliente=$idCliente, restaurante=$idRestaurante, dirección=$direccionEntrega")
            println("[DEBUG] Combos recibidos: ${combosJson.toString()}") // 2. Verificar combos

            val combos = combosJson.map { combo ->
                val comboObj = combo.asJsonObject
                ComboPedido(
                    id_combo = comboObj.get("id_combo").asInt.also {
                        println("[DEBUG] Procesando combo ID: $it")
                    },
                    cantidad = comboObj.get("cantidad").asInt
                )
            }

            println("[DEBUG] Enviando a controller: ${combos.size} combos")
            val resultado = pedidoController.crearPedidoCompleto(
                idCliente, idRestaurante, direccionEntrega, combos
            ).also {
                println("[DEBUG] Resultado del controller: $it") // 3. Verificar resultado
            }

            val responseJson = JsonObject().apply {
                addProperty("success", true)
                addProperty("id_pedido", (resultado["id_pedido"] as? Number)?.toInt() ?: -1)
                addProperty("id_repartidor", (resultado["id_repartidor"] as? Number)?.toInt() ?: -1)
            }

            println("[DEBUG] Respuesta final: ${responseJson.toString()}") // 4. Verificar respuesta
            sendJsonResponse(writer, responseJson)

        } catch (e: Exception) {
            println("[ERROR] ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            sendErrorResponse(writer, "Error al crear pedido: ${e.message}")
        }
    }

    private fun sendJsonResponse(writer: PrintWriter, response: JsonObject) {
        val json = response.toString()
        writer.println("HTTP/1.1 200 OK")
        writer.println("Content-Type: application/json")
        writer.println("Content-Length: ${json.toByteArray().size}")
        writer.println()
        writer.println(json)
    }

    private fun handleAgregarComboPedido(writer: PrintWriter, idPedido: Int?, body: String) {
        try {
            if (idPedido == null) {
                sendErrorResponse(writer, "ID de pedido inválido")
                return
            }

            val json = JsonParser.parseString(body).asJsonObject
            val idCombo = json.get("id_combo").asInt
            val cantidad = json.get("cantidad").asInt

            val idDetalle = pedidoController.agregarComboPedido(idPedido, idCombo, cantidad)
            sendJsonResponse(writer, mapOf("success" to true, "id_detalle" to idDetalle))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al agregar combo al pedido: ${e.message}")
        }
    }

    private fun handleFinalizarPedido(writer: PrintWriter, idPedido: Int?) {
        try {
            if (idPedido == null) {
                sendErrorResponse(writer, "ID de pedido inválido")
                return
            }

            val totales = pedidoController.finalizarPedido(idPedido)
            sendJsonResponse(writer, mapOf(
                "success" to true,
                "subtotal" to (totales["subtotal"] ?: 0.0),
                "transporte" to (totales["transporte"] ?: 0.0),
                "iva" to (totales["iva"] ?: 0.0),
                "total" to (totales["total"] ?: 0.0)
            ))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al finalizar pedido: ${e.message}")
        }
    }

    private fun handleObtenerPedidosCliente(writer: PrintWriter, idCliente: Int?) {
        try {
            if (idCliente == null) {
                sendErrorResponse(writer, "ID de cliente inválido")
                return
            }

            val pedidos = pedidoController.obtenerPedidosCliente(idCliente)
            sendJsonResponse(writer, mapOf("success" to true, "pedidos" to pedidos))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener pedidos del cliente: ${e.message}")
        }
    }

    // ------------------------- Handlers para Repartidores -------------------------
    private fun handleObtenerRepartidores(writer: PrintWriter) {
        try {
            val repartidores = repartidorController.obtenerRepartidoresCeroAmonestaciones()
            sendJsonResponse(writer, mapOf("success" to true, "repartidores" to repartidores))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener repartidores: ${e.message}")
        }
    }

    private fun handleRepartidoresCeroAmonestaciones(writer: PrintWriter) {
        try {
            val repartidores = repartidorController.obtenerRepartidoresCeroAmonestaciones()
            sendJsonResponse(writer, mapOf("success" to true, "repartidores" to repartidores))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener repartidores: ${e.message}")
        }
    }

    private fun handleAsignarAmonestacion(writer: PrintWriter, idRepartidor: Int?) {
        try {
            if (idRepartidor == null) {
                sendErrorResponse(writer, "ID de repartidor inválido")
                return
            }

            val amonestaciones = repartidorController.asignarAmonestacion(idRepartidor)
            sendJsonResponse(writer, mapOf("success" to true, "amonestaciones" to amonestaciones))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al asignar amonestación: ${e.message}")
        }
    }

    private fun handleRegistrarRepartidor(writer: PrintWriter, body: String) {
        try {
            println("JSON recibido: $body")
            val request = gson.fromJson(body, RepartidorRegistroRequest::class.java)

            // Validación completa de campos
            when {
                request.cedula.isBlank() -> {
                    sendErrorResponse(writer, "La cédula es obligatoria", 400)
                    return
                }
                request.nombre.isBlank() -> {
                    sendErrorResponse(writer, "El nombre es obligatorio", 400)
                    return
                }
                request.costo_por_km <= 0 -> {
                    sendErrorResponse(writer, "El costo por km debe ser mayor a 0", 400)
                    return
                }
                request.correo.isBlank() || !isValidEmail(request.correo) -> {
                    sendErrorResponse(writer, "El correo electrónico no es válido", 400)
                    return
                }
                request.telefono.isBlank() -> {
                    sendErrorResponse(writer, "El teléfono es obligatorio", 400)
                    return
                }
            }

            // Registrar el usuario
            val idUsuario = registrarUsuarioComoRepartidor(request)
            println("Usuario registrado con ID: $idUsuario")

            // Registrar el repartidor
            val idRepartidor = repartidorController.registrarRepartidor(
                idUsuario,
                request.costo_por_km
            )
            println("Repartidor registrado con ID: $idRepartidor")

            sendJsonResponse(writer, mapOf(
                "success" to true,
                "id_repartidor" to idRepartidor,
                "id_usuario" to idUsuario,
                "message" to "Repartidor registrado exitosamente"
            ))
        } catch (e: Exception) {
            println("Error durante el registro: ${e.message}")
            sendErrorResponse(writer, "Error al registrar repartidor: ${e.message}")
        }
    }

    private fun handleObtenerPedidosRepartidor(writer: PrintWriter, idRepartidor: Int?, estado: String?) {
        try {
            if (idRepartidor == null) {
                sendErrorResponse(writer, "ID de repartidor inválido")
                return
            }

            val pedidos = pedidoController.obtenerPedidosRepartidor(idRepartidor, estado)
            sendJsonResponse(writer, mapOf("success" to true, "pedidos" to pedidos))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener pedidos del repartidor: ${e.message}")
        }
    }

    private fun registrarUsuarioComoRepartidor(repartidor: RepartidorRegistroRequest): Int {
        val usuario = Usuario(
            id_usuario = 0, // Auto-generado
            cedula = repartidor.cedula,
            nombre = repartidor.nombre,
            correo = repartidor.correo,
            direccion = repartidor.direccion,
            telefono = repartidor.telefono,
            numero_tarjeta = "129927771", // Opcional para repartidores
            tipo = "repartidor",
            estado = "activo"
        )
        return usuarioController.registrarUsuario(usuario)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$"
        return email.matches(emailRegex.toRegex())
    }


    // ------------------------- Handlers para Reportes -------------------------

    private fun handleReporteVentasRestaurantes(writer: PrintWriter) {
        try {
            val reporte = restauranteController.reporteVentasRestaurantes()
            sendJsonResponse(writer, mapOf("success" to true, "reporte" to reporte))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al generar reporte de ventas: ${e.message}")
        }
    }

    private fun handleReporteQuejasRepartidores(writer: PrintWriter) {
        try {
            val quejas = repartidorController.reporteQuejasRepartidores()
            sendJsonResponse(writer, mapOf("success" to true, "quejas" to quejas))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al generar reporte de quejas: ${e.message}")
        }
    }

    // ------------------------- Handlers para Combos -------------------------
    private fun handleRegistrarCombo(writer: PrintWriter, body: String) {
        try {
            val combo = gson.fromJson(body, ComboRequest::class.java)
            val idCombo = restauranteController.registrarCombo(combo)

            sendJsonResponse(writer, mapOf("success" to true, "id_combo" to idCombo))
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorResponse(writer, "Error al registrar combo: ${e.message}")
        }
    }

    // ------------------------- Handlers para Pedidos Completos -------------------------
    private fun handleCrearPedidoCompleto(writer: PrintWriter, body: String) {
        try {
            val json = JsonParser.parseString(body).asJsonObject
            val idCliente = json.get("id_cliente").asInt
            val idRestaurante = json.get("id_restaurante").asInt
            val direccionEntrega = json.get("direccion_entrega").asString
            val combosJson = json.getAsJsonArray("combos")

            val combos = combosJson.map { combo ->
                val comboObj = combo.asJsonObject
                ComboPedido(
                    id_combo = comboObj.get("id_combo").asInt,
                    cantidad = comboObj.get("cantidad").asInt
                )
            }

            val resultado = pedidoController.crearPedidoCompleto(
                idCliente, idRestaurante, direccionEntrega, combos
            )

            sendJsonResponse(writer, mapOf("success" to true, "pedido" to resultado))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al crear pedido completo: ${e.message}")
        }
    }

    private fun handleObtenerDetallePedido(writer: PrintWriter, idPedido: Int?, idCliente: Int?) {
        try {
            if (idPedido == null || idCliente == null) {
                sendErrorResponse(writer, "Parámetros inválidos")
                return
            }

            val detalle = pedidoController.obtenerDetallePedidoCompleto(idPedido, idCliente)
            sendJsonResponse(writer, mapOf("success" to true, "pedido" to detalle))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al obtener detalle del pedido: ${e.message}")
        }
    }

    private fun handleListarPedidosCliente(writer: PrintWriter, idCliente: Int?, estado: String?) {
        try {
            if (idCliente == null) {
                sendErrorResponse(writer, "ID de cliente inválido")
                return
            }

            val pedidos = pedidoController.listarPedidosCliente(idCliente, estado)
            sendJsonResponse(writer, mapOf("success" to true, "pedidos" to pedidos))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al listar pedidos del cliente: ${e.message}")
        }
    }

    private fun handleActualizarEstadoPedido(writer: PrintWriter, idPedido: Int?, body: String) {
        try {
            if (idPedido == null) {
                sendErrorResponse(writer, "ID de pedido inválido")
                return
            }

            val json = JsonParser.parseString(body).asJsonObject
            val nuevoEstado = json.get("estado").asString

            val resultado = pedidoController.actualizarEstadoPedido(idPedido, nuevoEstado)

            if (resultado) {
                sendJsonResponse(writer, mapOf("success" to true, "message" to "Estado actualizado"))
            } else {
                sendErrorResponse(writer, "Error al actualizar estado del pedido")
            }
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al actualizar estado: ${e.message}")
        }
    }

    private fun handleCalificarPedido(writer: PrintWriter, body: String) {
        println("=== INICIO DE CALIFICACIÓN ===")
        println("Body recibido: $body")
        try {
            val calificacion = gson.fromJson(body, CalificacionRequest::class.java)
            println("JSON parseado: $calificacion")

            // Validar puntajes
            if (calificacion.puntaje_repartidor !in 1..5 || calificacion.puntaje_restaurante !in 1..5) {
                sendErrorResponse(writer, "Los puntajes deben estar entre 1 y 5", 400)
                return
            }

            println("Llamando a pedidoController.calificarPedido...")
            val resultado = pedidoController.calificarPedido(
                calificacion.id_pedido,
                calificacion.id_cliente,
                calificacion.puntaje_repartidor,
                calificacion.puntaje_restaurante,
                calificacion.comentario,
                calificacion.queja
            )

            println("Resultado del controlador: $resultado")
            sendJsonResponse(writer, mapOf(
                "success" to resultado,
                "message" to if (resultado) "Pedido calificado exitosamente" else "Error al calificar pedido"
            ))
        } catch (e: Exception) {
            sendErrorResponse(writer, "Error al calificar pedido: ${e.message}")
        }
        println("=== FIN DE CALIFICACIÓN ===")
    }

    // Método auxiliar para obtener parámetros de consulta
    private fun getQueryParam(path: String, paramName: String): String? {
        return path.split('?').getOrNull(1)
            ?.split('&')
            ?.map { it.split('=') }
            ?.firstOrNull { it[0] == paramName }
            ?.getOrNull(1)
    }


    // ------------------------- Métodos auxiliares -------------------------
    private fun handleOptionsRequest(writer: PrintWriter) {
        writer.println("HTTP/1.1 200 OK")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Access-Control-Allow-Methods: POST, GET, PUT, DELETE, OPTIONS")
        writer.println("Access-Control-Allow-Headers: Content-Type")
        writer.println("Connection: close")
        writer.println()
    }

    private fun sendJsonResponse(writer: PrintWriter, data: Map<String, Any>, statusCode: Int = 200) {
        writer.println("HTTP/1.1 $statusCode")
        writer.println("Content-Type: application/json")
        writer.println("Access-Control-Allow-Origin: *")
        writer.println("Connection: close")
        writer.println()
        writer.println(gson.toJson(data))
    }

    private fun sendErrorResponse(writer: PrintWriter, message: String, statusCode: Int = 400) {
        sendJsonResponse(writer, mapOf("success" to false, "error" to message), statusCode)
    }
}
