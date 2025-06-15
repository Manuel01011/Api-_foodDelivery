package com.example.proyectomoviles.backend.controllers
import com.example.proyectomoviles.backend.models.Cliente
import com.example.proyectomoviles.backend.models.Usuario
import com.example.proyectomoviles.backend.service.DatabaseDAO

class UsuarioController {

    // Registrar nuevo usuario
    fun registrarUsuario(usuario: Usuario): Int {
        val procedureName = "sp_registrar_usuario"
        val result = DatabaseDAO.executeStoredProcedureWithResults(
            procedureName,
            usuario.cedula,
            usuario.nombre,
            usuario.correo,
            usuario.direccion,
            usuario.telefono,
            usuario.numero_tarjeta,
            usuario.tipo
        )

        if (result != null && result.next()) {
            return result.getInt("id_usuario")
        } else {
            throw Exception("Error al registrar usuario: no se devolvió ningún ID.")
        }
    }

    // Verificar si usuario existe por cédula
    fun verificarUsuario(cedula: String): Usuario? {
        val procedureName = "sp_verificar_usuario"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, cedula)

        return result?.let {
            if (it.next()) {
                Usuario(
                    id_usuario = it.getInt("id_usuario"),
                    cedula = cedula,
                    nombre = it.getString("nombre"),
                    tipo = it.getString("tipo"),
                    estado = it.getString("estado")
                )
            } else {
                null
            }
        }
    }

    fun verificarRestaurante(cedulaJuridica: String): Usuario? {
        val conn = DatabaseDAO.getConncection()
        try {
            val sql = "SELECT id_restaurante, nombre, activo FROM Restaurantes WHERE cedula_juridica = ?"
            val ps = conn!!.prepareStatement(sql)
            ps.setString(1, cedulaJuridica)

            val rs = ps.executeQuery()
            return if (rs.next()) {
                Usuario(
                    id_usuario = rs.getInt("id_restaurante"),
                    nombre = rs.getString("nombre"),
                    tipo = "restaurante",
                    estado = if (rs.getBoolean("activo")) "activo" else "suspendido",
                    // Otros campos pueden ser null o valores por defecto
                    cedula = cedulaJuridica,
                    correo = "",
                    direccion = "",
                    telefono = "",
                    numero_tarjeta = ""
                )
            } else {
                null
            }
        } finally {
            conn?.close()
        }
    }

    // Obtener cliente por ID
    fun obtenerCliente(idUsuario: Int): Cliente {
        val procedureName = "sp_obtener_cliente"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName, idUsuario)

        return result?.let {
            if (it.next()) {
                Cliente(
                    id_usuario = it.getInt("id_usuario"),
                    cedula = it.getString("cedula"),
                    nombre = it.getString("nombre"),
                    correo = it.getString("correo"),
                    direccion = it.getString("direccion"),
                    telefono = it.getString("telefono"),
                    tipo = it.getString("tipo"),
                    estado = it.getString("estado")
                )
            } else {
                throw Exception("Cliente no encontrado")
            }
        } ?: throw Exception("Error al obtener cliente")
    }

    // Listar clientes por estado
    fun listarClientes(estado: String): List<Cliente> {
        val procedureName ="sp_GetAllclientes"
        val result = DatabaseDAO.executeStoredProcedureWithResults(procedureName)

        val clientes = mutableListOf<Cliente>()
        result?.let {
            while (it.next()) {
                clientes.add(
                    Cliente(
                        id_usuario = it.getInt("id_usuario"),
                        cedula = it.getString("cedula"),
                        nombre = it.getString("nombre"),
                        correo = it.getString("correo"),
                        direccion = it.getString("direccion"),
                        telefono = it.getString("telefono"),
                        tipo = it.getString("tipo"),
                        estado = it.getString("estado"),

                    )
                )
            }
        }
        return clientes
    }
}