# Documentación de API - Sistema de Delivery de Comida

## Descripción General

Esta API proporciona los servicios backend para una aplicación de delivery de comida, manejando:

- Gestión de usuarios (clientes, repartidores)
- Operaciones de restaurantes (registro, menús)
- Procesamiento de pedidos
- Asignación y seguimiento de repartidores
- Generación de reportes

## Componentes Principales

### 1. Gestión de Usuarios
- Registro y autenticación de clientes y repartidores
- Verificación de usuarios por cédula
- Administración de perfiles y estados

### 2. Módulo de Restaurantes
- Registro y listado de restaurantes
- Gestión de combos/menús
- Procesamiento de pedidos (estados, detalles)
- Actualización de estados de pedidos

### 3. Sistema de Pedidos
- Creación de pedidos completos (con múltiples combos)
- Seguimiento de estado (pendiente, preparación, en camino, entregado)
- Cálculo automático de totales (subtotal, transporte, IVA)
- Sistema de calificaciones y quejas

### 4. Gestión de Repartidores
- Asignación automática de repartidores
- Sistema de amonestaciones
- Registro con validación de datos
- Reportes de desempeño

### 5. Reportes y Analytics
- Restaurantes más populares
- Ventas por restaurante
- Quejas sobre repartidores


## Tecnologías Utilizadas

- **Lenguaje**: Kotlin
- **Protocolo**: HTTP/1.1
- **Serialización**: JSON (Gson)
- **Patrón**: Controller-Service
- **Concurrencia**: Multi-threading para manejo de conexiones

