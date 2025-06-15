package com.example.proyectomoviles.backend.service

import com.example.banner.backend.service.GlobalException
import com.example.banner.backend.service.NoDataException
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types


object DatabaseDAO {

    private val dbHelper = service()
    private val conexion = dbHelper.getConnection()

    // Obtener conexión
    fun getConnection(): Connection? {
        return dbHelper.getConnection()?: throw GlobalException("No se pudo obtener la conexión a la base de datos")
    }

    fun executeStoredProcedureForList(
        procedureName: String,
        returnType: Class<*>,
        vararg params: Any
    ): List<Any> {
        val gson = Gson()
        val result = mutableListOf<Any>()
        var conn: Connection? = null
        try {
            conn = getConnection() ?: return emptyList()
            val placeholders = "?,".repeat(params.size).dropLast(1)
            val call = "{call $procedureName($placeholders)}"

            conn.prepareCall(call).use { cs ->
                params.forEachIndexed { index, param ->
                    cs.setObject(index + 1, param)
                }

                val hasResults = cs.execute()
                if (hasResults) {
                    cs.resultSet.use { rs ->
                        val metaData = rs.metaData
                        val columnCount = metaData.columnCount

                        while (rs.next()) {
                            val jsonObj = JsonObject()
                            for (i in 1..columnCount) {
                                val columnName = metaData.getColumnLabel(i)
                                jsonObj.addProperty(columnName, rs.getString(i))
                            }
                            result.add(gson.fromJson(jsonObj, returnType))
                        }
                    }
                }
            }
            return result
        } catch (e: Exception) {
            e.printStackTrace()
            throw GlobalException("Error al ejecutar procedimiento: ${e.message}")
        } finally {
            conn?.let { dbHelper.closeConnection(it) }
        }
    }

    // Método para ejecutar consultas SELECT
    fun executeQuery(query: String, vararg params: Any): ResultSet? {
        return try {
            val conn = com.example.proyectomoviles.backend.service.DatabaseDAO.getConnection() ?: return null
            val stmt = conn.prepareStatement(query)
            for ((index, param) in params.withIndex()) {
                stmt.setObject(index + 1, param)
            }
            stmt.executeQuery()
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
        com.example.proyectomoviles.backend.service.DatabaseDAO.dbHelper.closeConnection(com.example.proyectomoviles.backend.service.DatabaseDAO.conexion)
    }

    fun getConncection(): Connection? {
        return try {
            com.example.proyectomoviles.backend.service.DatabaseDAO.getConnection()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }



    fun executeStoredProcedureWithNullableParams(procedureName: String, params: List<Any?>): ResultSet? {
        return getConnection()?.let { conn -> // Safe call con let
            try {
                val call = "{call $procedureName(?, ?, ?, ?, ?, ?, ?, ?)}"
                conn.prepareCall(call).use { cs ->
                    params.forEachIndexed { index, param ->
                        when (param) {
                            null -> cs.setNull(index + 1, Types.NULL)
                            is Int -> cs.setInt(index + 1, param)
                            is String -> cs.setString(index + 1, param)
                            else -> throw IllegalArgumentException("Tipo de parámetro no soportado")
                        }
                    }

                    cs.execute()
                    cs.resultSet
                }
            } catch (e: SQLException) {
                e.printStackTrace()
                null
            } finally {
                try {
                    conn.close()
                } catch (e: SQLException) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ejecutar metodos que editan o eliminan no muestran nada
    fun executeStoredProcedure(procedureName: String, vararg params: Any): Boolean {
        val conn = com.example.proyectomoviles.backend.service.DatabaseDAO.getConnection() ?: return false
        return try {
            // Se prepara la llamada al procedimiento con el número dinámico de parámetros
            val placeholders = "?,".repeat(params.size).dropLast(1) // Crea la cadena de placeholders dinámica
            val callableStatement: CallableStatement = conn.prepareCall("{call $procedureName($placeholders)}")

            for ((index, param) in params.withIndex()) {
                callableStatement.setObject(index + 1, param)
            }
            callableStatement.execute()
            callableStatement.close()
            true
        } catch (e: SQLException) {
            throw GlobalException("Error al ejecutar procedimiento almacenado: ${e.message}")
        } finally {
            com.example.proyectomoviles.backend.service.DatabaseDAO.dbHelper.closeConnection(com.example.proyectomoviles.backend.service.DatabaseDAO.conexion)
        }
    }

    //procedimiento que devuelve un cursor de resultado
    fun executeStoredProcedureWithResults(procedureName: String, vararg params: Any?): ResultSet? {
        return try {
            val conn = com.example.proyectomoviles.backend.service.DatabaseDAO.getConnection() ?: return null
            val placeholders = "?,".repeat(params.size).dropLast(1) // Generar "?,?,?"
            val callableStatement: CallableStatement = conn.prepareCall("{CALL $procedureName($placeholders)}")

            for ((index, param) in params.withIndex()) {
                if (param == null) {
                    callableStatement.setNull(index + 1, Types.VARCHAR) // Puedes ajustar esto si esperas otros tipos
                } else {
                    when (param) {
                        is String -> callableStatement.setString(index + 1, param)
                        is Int -> callableStatement.setInt(index + 1, param)
                        is Long -> callableStatement.setLong(index + 1, param)
                        is Double -> callableStatement.setDouble(index + 1, param)
                        else -> callableStatement.setObject(index + 1, param)
                    }
                }
            }

            val resultSet = callableStatement.executeQuery() // Ejecutar y obtener resultados
            if (!resultSet.isBeforeFirst) {
                throw NoDataException("No se encontraron datos al ejecutar el procedimiento almacenado: $procedureName")
            }

            resultSet
        } catch (e: SQLException) {
            throw GlobalException("Error al ejecutar procedimiento almacenado con resultados: ${e.message}")
        } finally {
            com.example.proyectomoviles.backend.service.DatabaseDAO.dbHelper.closeConnection(com.example.proyectomoviles.backend.service.DatabaseDAO.conexion)
        }
    }

    //procedimiento que devuelve solo un resultado
    fun executeStoredProcedureForSingleResult(procedureName: String): ResultSet? {
        return try {
            val conn = com.example.proyectomoviles.backend.service.DatabaseDAO.getConnection() ?: return null
            val callableStatement: CallableStatement = conn.prepareCall("{CALL $procedureName()}")
            callableStatement.executeQuery()

        } catch (e: SQLException) {
            throw GlobalException("Error al ejecutar el procedimiento almacenado $procedureName: ${e.message}")
        } finally {
            com.example.proyectomoviles.backend.service.DatabaseDAO.dbHelper.closeConnection(com.example.proyectomoviles.backend.service.DatabaseDAO.conexion)
        }

    }
}