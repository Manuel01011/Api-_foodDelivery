# 🚀 Documentación de API - Sistema de Delivery de Comida 🍔

![Banner de Food Delivery](https://img.freepik.com/vector-gratis/plantilla-banner-comida-entregada_23-2148983004.jpg)

## 🌐 Descripción General

Esta API proporciona los servicios backend para una aplicación de delivery de comida [🔗 GitHub](https://github.com/Manuel01011/food-delivery-app), manejando:

- 👥 Gestión de usuarios (clientes, repartidores)
- 🏪 Operaciones de restaurantes (registro, menús)
- 📦 Procesamiento de pedidos
- 🛵 Asignación y seguimiento de repartidores
- 📊 Generación de reportes

## 🧩 Componentes Principales

### 1. 👤 Gestión de Usuarios
- 📝 Registro y autenticación de clientes y repartidores
- 🔍 Verificación de usuarios por cédula
- ⚙️ Administración de perfiles y estados

### 2. 🍽️ Módulo de Restaurantes
- ➕ Registro y listado de restaurantes
- 📋 Gestión de combos/menús
- 🔄 Procesamiento de pedidos (estados, detalles)
- 🆙 Actualización de estados de pedidos

### 3. 📦 Sistema de Pedidos
- 🛒 Creación de pedidos completos (con múltiples combos)
- 🗺️ Seguimiento de estado:
  - ⏳ Pendiente
  - 👨‍🍳 Preparación
  - 🚗 En camino
  - ✅ Entregado
- 🧮 Cálculo automático de totales:
  - 💰 Subtotal
  - 🚚 Transporte
  - 🏛️ IVA
- ⭐ Sistema de calificaciones y quejas

### 4. 🛵 Gestión de Repartidores
- 🤖 Asignación automática de repartidores
- ⚠️ Sistema de amonestaciones
- 📝 Registro con validación de datos
- 📈 Reportes de desempeño

### 5. 📊 Reportes y Analytics
- 🔥 Restaurantes más populares
- 💸 Ventas por restaurante
- 😠 Quejas sobre repartidores

## 🛠️ Tecnologías Utilizadas

| Tecnología | Icono | Descripción |
|------------|-------|-------------|
| **Kotlin** | <img src="https://upload.wikimedia.org/wikipedia/commons/7/74/Kotlin_Icon.png" width="20"> | Lenguaje principal |
| **HTTP/1.1** | 🌐 | Protocolo de comunicación |
| **JSON (Gson)** | {} | Serialización de datos |
| **Controller-Service** | 🏗️ | Patrón arquitectónico |
| **Multi-threading** | 🧵 | Manejo de conexiones |

## 🔄 Flujos Principales

```mermaid
graph TD
    A[Cliente] -->|Verifica| B(Usuario)
    B -->|Explora| C[Restaurantes]
    C -->|Selecciona| D[Combos]
    D -->|Realiza| E[Pedido]
    E -->|Califica| F[Servicio]
