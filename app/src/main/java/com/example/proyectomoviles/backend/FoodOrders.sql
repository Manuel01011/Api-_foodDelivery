use foodorders;
-- tabla de usuarios
CREATE TABLE Usuarios (
    id_usuario INT PRIMARY KEY AUTO_INCREMENT,
    cedula VARCHAR(20) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    correo VARCHAR(100) UNIQUE NOT NULL,
    direccion TEXT NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    numero_tarjeta VARCHAR(20) NOT NULL,
    tipo ENUM('cliente', 'repartidor') NOT NULL,
    estado ENUM('activo', 'suspendido') DEFAULT 'activo',
    fecha_registro DATETIME DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE Usuarios 
MODIFY COLUMN tipo ENUM('cliente', 'repartidor', 'admin') NOT NULL;

-- tabla de repartidors
CREATE TABLE Repartidores (
    id_repartidor INT PRIMARY KEY,
    id_usuario INT UNIQUE NOT NULL,
    estado_disponibilidad ENUM('disponible', 'ocupado') DEFAULT 'disponible',
    distancia_pedido DECIMAL(10,2) DEFAULT 0,
    km_recorridos_diarios DECIMAL(10,2) DEFAULT 0,
    costo_por_km DECIMAL(10,2) NOT NULL,
    amonestaciones INT DEFAULT 0,
    FOREIGN KEY (id_usuario) REFERENCES Usuarios(id_usuario)
);  

-- tabla de restaurantes
CREATE TABLE Restaurantes (
    id_restaurante INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    cedula_juridica VARCHAR(20) UNIQUE NOT NULL,
    direccion TEXT NOT NULL,
    tipo_comida VARCHAR(50) NOT NULL,
    activo BOOLEAN DEFAULT TRUE
);

-- tabla de combos
CREATE TABLE Combos (
    id_combo INT PRIMARY KEY AUTO_INCREMENT,
    id_restaurante INT NOT NULL,
    numero_combo INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    descripcion TEXT,
    precio DECIMAL(10,2) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (id_restaurante) REFERENCES Restaurantes(id_restaurante),
    UNIQUE KEY (id_restaurante, numero_combo)
);

-- tabla de pedidos
CREATE TABLE Pedidos (
    id_pedido INT PRIMARY KEY AUTO_INCREMENT,
    id_cliente INT NOT NULL,
    id_restaurante INT NOT NULL,
    id_repartidor INT,
    fecha_pedido DATETIME DEFAULT CURRENT_TIMESTAMP,
    estado ENUM('pendiente', 'preparacion', 'en_camino', 'entregado', 'cancelado') DEFAULT 'pendiente',
    subtotal DECIMAL(10,2) NOT NULL,
    costo_transporte DECIMAL(10,2) NOT NULL,
    iva DECIMAL(10,2) NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    direccion_entrega TEXT NOT NULL,
    metodo_pago VARCHAR(50) DEFAULT 'tarjeta',
    FOREIGN KEY (id_cliente) REFERENCES Usuarios(id_usuario),
    FOREIGN KEY (id_restaurante) REFERENCES Restaurantes(id_restaurante),
    FOREIGN KEY (id_repartidor) REFERENCES Repartidores(id_repartidor)
);

-- tabla detalle de pedidos
CREATE TABLE Detalle_Pedidos (
    id_detalle INT PRIMARY KEY AUTO_INCREMENT,
    id_pedido INT NOT NULL,
    id_combo INT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (id_pedido) REFERENCES Pedidos(id_pedido),
    FOREIGN KEY (id_combo) REFERENCES Combos(id_combo)
);

-- tabla calificaciones
CREATE TABLE Calificaciones (
    id_calificacion INT PRIMARY KEY AUTO_INCREMENT,
    id_pedido INT UNIQUE NOT NULL,
    id_cliente INT NOT NULL,
    id_repartidor INT NOT NULL,
    puntaje_repartidor INT CHECK (puntaje_repartidor BETWEEN 1 AND 5),
    puntaje_restaurante INT CHECK (puntaje_restaurante BETWEEN 1 AND 5),
    comentario TEXT,
    queja BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id_pedido) REFERENCES Pedidos(id_pedido),
    FOREIGN KEY (id_cliente) REFERENCES Usuarios(id_usuario),
    FOREIGN KEY (id_repartidor) REFERENCES Repartidores(id_repartidor)
);

-- tabla de quejas
CREATE TABLE Quejas (
    id_queja INT PRIMARY KEY AUTO_INCREMENT,
    id_calificacion INT NOT NULL,
    descripcion TEXT NOT NULL,
    fecha_queja DATETIME DEFAULT CURRENT_TIMESTAMP,
    resuelta BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id_calificacion) REFERENCES Calificaciones(id_calificacion)
);

-- procedimientos para registrar usuario / clientes
DELIMITER //
CREATE PROCEDURE sp_registrar_usuario(
    IN p_cedula VARCHAR(20),
    IN p_nombre VARCHAR(100),
    IN p_correo VARCHAR(100),
    IN p_direccion TEXT,
    IN p_telefono VARCHAR(20),
    IN p_numero_tarjeta VARCHAR(20),
    IN p_tipo ENUM('cliente', 'repartidor')
)
BEGIN
    DECLARE user_count INT;
    
    -- Verificar si la cédula ya existe
    SELECT COUNT(*) INTO user_count FROM Usuarios WHERE cedula = p_cedula;
    
    IF user_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ya existe un usuario con esta cédula';
    ELSE
        -- Insertar nuevo usuario
        INSERT INTO Usuarios (cedula, nombre, correo, direccion, telefono, numero_tarjeta, tipo)
        VALUES (p_cedula, p_nombre, p_correo, p_direccion, p_telefono, p_numero_tarjeta, p_tipo);
        
        SELECT LAST_INSERT_ID() AS id_usuario;
    END IF;
END //
DELIMITER ;

-- obtener las calificaciones por cada repartidor
CALL obtener_calificaciones_repartidores();
select * from calificaciones

DELIMITER $$
CREATE PROCEDURE obtener_calificaciones_repartidores()
BEGIN
    SELECT 
        r.id_repartidor,
        u.nombre AS nombre_repartidor,
        c.puntaje_repartidor,
        c.comentario,
        c.queja,
        c.id_pedido
    FROM Calificaciones c
    INNER JOIN Repartidores r ON c.id_repartidor = r.id_repartidor
    INNER JOIN Usuarios u ON r.id_usuario = u.id_usuario
    ORDER BY r.id_repartidor, c.id_calificacion;
END $$
DELIMITER ;

-- ver el restaurante con mayor cantidad de pedidos
CALL sp_listar_restaurantes_por_pedidos();

DELIMITER //
CREATE PROCEDURE sp_listar_restaurantes_por_pedidos()
BEGIN
    SELECT 
        r.id_restaurante,
        r.nombre,
        r.tipo_comida,
        COUNT(p.id_pedido) AS total_pedidos
    FROM 
        Restaurantes r
    LEFT JOIN 
        Pedidos p ON r.id_restaurante = p.id_restaurante 
        AND p.estado != 'cancelado'
    WHERE 
        r.activo = TRUE
    GROUP BY 
        r.id_restaurante, r.nombre, r.tipo_comida
    ORDER BY 
        total_pedidos DESC;
END //
DELIMITER ;

-- Restaurantes y sus ventas
CALL sp_ventas_por_restaurante_simple();

DELIMITER //
CREATE PROCEDURE sp_ventas_por_restaurante_simple()
BEGIN
    -- Calcular el total general primero
    SET @total_general = (
        SELECT SUM(p.total) 
        FROM Pedidos p
        JOIN Restaurantes r ON p.id_restaurante = r.id_restaurante
        WHERE r.activo = TRUE AND p.estado != 'cancelado'
    );
    
    -- Consulta principal con cálculo directo del porcentaje
    SELECT 
        r.id_restaurante,
        r.nombre AS nombre_restaurante,
        r.tipo_comida,
        SUM(p.total) AS total_vendido,
        ROUND((SUM(p.total) / NULLIF(@total_general, 0)) * 100, 2) AS porcentaje_total,
        @total_general AS ventas_totales_generales
    FROM 
        Restaurantes r
    JOIN 
        Pedidos p ON r.id_restaurante = p.id_restaurante
    WHERE 
        r.activo = TRUE
        AND p.estado != 'cancelado'
    GROUP BY 
        r.id_restaurante, r.nombre, r.tipo_comida
    ORDER BY 
        total_vendido DESC;
END //
DELIMITER ;


-- pruebas de pedido----
select * from pedidos;
CALL sp_crear_pedido_completo(
    22, -- id_usuarios
    2, -- id_restaurante
    'Calle 23, Heredua', -- dirección entrega
    '[{"id_combo": 5, "cantidad": 1}, {"id_combo": 6, "cantidad": 1}]' -- JSON de combos
);

select * from restaurantes;
select * from pedidos;
Select * from Usuarios;
select * from repartidores;
Select * from Calificaciones;


-- obtener pedidos de un repartidor

select * from repartidores;
call sp_obtener_pedidos_repartidor_por_estado(7,'en_camino');
drop procedure sp_obtener_pedidos_repartidor;

DELIMITER //
CREATE PROCEDURE sp_obtener_pedidos_repartidor_por_estado(
    IN p_id_repartidor INT,
    IN p_estado VARCHAR(20)
)
BEGIN
    -- Si p_estado es NULL, muestra todos los pedidos
    IF p_estado IS NULL THEN
        SELECT 
            p.id_pedido,
            p.fecha_pedido,
            p.estado,
            p.total,
            r.nombre AS restaurante,
            r.direccion AS direccion_restaurante,
            u.nombre AS cliente,
            p.direccion_entrega,
            COUNT(dp.id_detalle) AS cantidad_items,
            TIMESTAMPDIFF(MINUTE, p.fecha_pedido, NOW()) AS minutos_transcurridos,
            CASE 
                WHEN p.estado = 'entregado' THEN 'Entregado'
                WHEN p.estado = 'en_camino' THEN 'En camino'
                WHEN p.estado = 'preparacion' THEN 'En preparación'
                WHEN p.estado = 'pendiente' THEN 'Pendiente de recoger'
                WHEN p.estado = 'cancelado' THEN 'Cancelado'
            END AS estado_descripcion,
            p.costo_transporte AS ganancia_repartidor
        FROM 
            Pedidos p
        JOIN 
            Restaurantes r ON p.id_restaurante = r.id_restaurante
        JOIN 
            Usuarios u ON p.id_cliente = u.id_usuario
        LEFT JOIN 
            Detalle_Pedidos dp ON p.id_pedido = dp.id_pedido
        WHERE 
            p.id_repartidor = p_id_repartidor
        GROUP BY 
            p.id_pedido
        ORDER BY 
            CASE 
                WHEN p.estado = 'en_camino' THEN 1
                WHEN p.estado = 'preparacion' THEN 2
                WHEN p.estado = 'pendiente' THEN 3
                ELSE 4
            END,
            p.fecha_pedido DESC;
    ELSE
        -- Si se especifica un estado, filtra por ese estado
        SELECT 
            p.id_pedido,
            p.fecha_pedido,
            p.estado,
            p.total,
            r.nombre AS restaurante,
            r.direccion AS direccion_restaurante,
            u.nombre AS cliente,
            p.direccion_entrega,
            COUNT(dp.id_detalle) AS cantidad_items,
            TIMESTAMPDIFF(MINUTE, p.fecha_pedido, NOW()) AS minutos_transcurridos,
            CASE 
                WHEN p.estado = 'entregado' THEN 'Entregado'
                WHEN p.estado = 'en_camino' THEN 'En camino'
                WHEN p.estado = 'preparacion' THEN 'En preparación'
                WHEN p.estado = 'pendiente' THEN 'Pendiente de recoger'
                WHEN p.estado = 'cancelado' THEN 'Cancelado'
            END AS estado_descripcion,
            p.costo_transporte AS ganancia_repartidor
        FROM 
            Pedidos p
        JOIN 
            Restaurantes r ON p.id_restaurante = r.id_restaurante
        JOIN 
            Usuarios u ON p.id_cliente = u.id_usuario
        LEFT JOIN 
            Detalle_Pedidos dp ON p.id_pedido = dp.id_pedido
        WHERE 
            p.id_repartidor = p_id_repartidor
            AND p.estado = p_estado
        GROUP BY 
            p.id_pedido
        ORDER BY 
            p.fecha_pedido DESC;
    END IF;
END //
DELIMITER ;



-- id el pedido y cliente
CALL sp_obtener_detalle_pedido_completo(5, 22);
-- id del cliente
CALL sp_listar_pedidos_cliente(22, null); -- Todos los pedidos

select * from pedidos;

Select * from detalle_pedidos;
DELETE FROM detalle_pedidos WHERE id_detalle = 3;
DELETE FROM detalle_pedidos WHERE id_pedido = 6;



-- id del cliente
CALL sp_listar_pedidos_cliente(21, 'entregado'); -- Solo pedidos entregados

-- id del pedido el que falta es el 22 del usuario 21
CALL sp_actualizar_estado_pedido(21, 'en_camino');
CALL sp_actualizar_estado_pedido(21, 'entregado');


-- calificar pedido
CALL sp_calificar_pedido(
    5, -- id_pedido
    22, -- id_cliente
    5, -- puntaje repartidor
    5, -- puntaje restaurante
    'Todo excelente, pero llegó un poco tarde', -- comentario
    FALSE -- es queja?
);
-- fin -------------

-- crear pedido completo
DELIMITER //
CREATE PROCEDURE sp_crear_pedido_completo(
    IN p_id_cliente INT,
    IN p_id_restaurante INT,
    IN p_direccion_entrega TEXT,
    IN p_combos_json JSON
)
BEGIN
    DECLARE v_id_pedido INT;
    DECLARE v_id_repartidor INT;
    DECLARE v_subtotal DECIMAL(10,2) DEFAULT 0;
    DECLARE v_costo_transporte DECIMAL(10,2) DEFAULT 0;
    DECLARE v_iva DECIMAL(10,2) DEFAULT 0;
    DECLARE v_total DECIMAL(10,2) DEFAULT 0;
    DECLARE i INT DEFAULT 0;
    DECLARE v_combo_count INT;
    DECLARE v_id_combo INT;
    DECLARE v_cantidad INT;
    DECLARE v_precio DECIMAL(10,2);
    DECLARE v_costo_por_km DECIMAL(10,2);
    DECLARE v_distancia_estimada DECIMAL(10,2);
    DECLARE v_error_msg VARCHAR(255);
    
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 1. Validar que el cliente existe y está activo
    IF NOT EXISTS (SELECT 1 FROM Usuarios WHERE id_usuario = p_id_cliente AND tipo = 'cliente' AND estado = 'activo') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cliente no válido o inactivo';
    END IF;
    
    -- 2. Validar que el restaurante existe y está activo
    IF NOT EXISTS (SELECT 1 FROM Restaurantes WHERE id_restaurante = p_id_restaurante AND activo = TRUE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Restaurante no válido o inactivo';
    END IF;
    
    -- 3. Asignar repartidor disponible (con menos amonestaciones y más cercano)
    SELECT r.id_repartidor, r.costo_por_km INTO v_id_repartidor, v_costo_por_km
    FROM Repartidores r
    JOIN Usuarios u ON r.id_usuario = u.id_usuario
    WHERE r.estado_disponibilidad = 'disponible' 
    AND u.estado = 'activo'
    ORDER BY r.amonestaciones ASC, RAND()
    LIMIT 1;
    
    -- 4. Calcular distancia estimada (simulación)
    SET v_distancia_estimada = 5 + (RAND() * 10); -- Entre 5 y 15 km
    
    -- 5. Crear el pedido inicial
    INSERT INTO Pedidos (
        id_cliente, id_restaurante, id_repartidor,
        direccion_entrega, estado,
        subtotal, costo_transporte, iva, total
    ) VALUES (
        p_id_cliente, p_id_restaurante, v_id_repartidor,
        p_direccion_entrega, 'pendiente',
        0, 0, 0, 0
    );
    
    SET v_id_pedido = LAST_INSERT_ID();
    
    -- 6. Procesar cada combo del JSON
    SET v_combo_count = JSON_LENGTH(p_combos_json);
    
    WHILE i < v_combo_count DO
        -- Obtener datos del combo
        SET v_id_combo = JSON_EXTRACT(p_combos_json, CONCAT('$[', i, '].id_combo'));
        SET v_cantidad = JSON_EXTRACT(p_combos_json, CONCAT('$[', i, '].cantidad'));
        
        -- Convertir valores JSON a tipos MySQL
        SET v_id_combo = CAST(v_id_combo AS UNSIGNED);
        SET v_cantidad = CAST(v_cantidad AS UNSIGNED);
        
        -- Validar que el combo existe y pertenece al restaurante
        SELECT precio INTO v_precio
        FROM Combos
        WHERE id_combo = v_id_combo 
        AND id_restaurante = p_id_restaurante
        AND activo = TRUE;
        
        IF v_precio IS NULL THEN
            SET v_error_msg = CONCAT('Combo no válido o no pertenece al restaurante: ', CAST(v_id_combo AS CHAR));
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error_msg;
        END IF;
        
        -- Calcular subtotal
        SET v_subtotal = v_subtotal + (v_precio * v_cantidad);
        
        -- Insertar detalle del pedido
        INSERT INTO Detalle_Pedidos (id_pedido, id_combo, cantidad, precio_unitario)
        VALUES (v_id_pedido, v_id_combo, v_cantidad, v_precio);
        
        SET i = i + 1;
    END WHILE;
    
    -- 7. Calcular costos finales
    SET v_costo_transporte = ROUND(v_distancia_estimada * v_costo_por_km, 2);
    SET v_iva = ROUND((v_subtotal + v_costo_transporte) * 0.13, 2); -- 13% de IVA
    SET v_total = ROUND(v_subtotal + v_costo_transporte + v_iva, 2);
    
    -- 8. Actualizar totales del pedido
    UPDATE Pedidos
    SET subtotal = v_subtotal,
        costo_transporte = v_costo_transporte,
        iva = v_iva,
        total = v_total
    WHERE id_pedido = v_id_pedido;
    
    -- 9. Actualizar repartidor (marcar como ocupado y actualizar distancia)
    IF v_id_repartidor IS NOT NULL THEN
        UPDATE Repartidores 
        SET estado_disponibilidad = 'ocupado',
            distancia_pedido = v_distancia_estimada,
            km_recorridos_diarios = km_recorridos_diarios + v_distancia_estimada
        WHERE id_repartidor = v_id_repartidor;
    END IF;
    
    COMMIT;
    
    -- Retornar resultados
    SELECT 
        v_id_pedido AS id_pedido,
        v_id_repartidor AS id_repartidor,
        v_subtotal AS subtotal,
        v_costo_transporte AS costo_transporte,
        v_iva AS iva,
        v_total AS total,
        v_distancia_estimada AS distancia_km,
        'Pedido creado exitosamente' AS mensaje;
END //
DELIMITER ;

-- obtener detalle de pedido
call sp_obtener_detalle_pedido_completo(5,22)
DELIMITER //
CREATE PROCEDURE sp_obtener_detalle_pedido_completo(
    IN p_id_pedido INT,
    IN p_id_cliente INT
)
BEGIN
    -- Verificar que el pedido pertenece al cliente
    IF NOT EXISTS (
        SELECT 1 FROM Pedidos 
        WHERE id_pedido = p_id_pedido 
        AND id_cliente = p_id_cliente
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido no encontrado o no pertenece al cliente';
    END IF;
    
    -- Obtener información básica del pedido
    SELECT 
        p.id_pedido,
        p.fecha_pedido,
        p.estado,
        p.subtotal,
        p.costo_transporte,
        p.iva,
        p.total,
        p.direccion_entrega,
        r.nombre AS restaurante,
        r.direccion AS direccion_restaurante,
        rep.id_repartidor,
        u.nombre AS nombre_repartidor,
        u.telefono AS telefono_repartidor,
        IFNULL((
            SELECT 1 FROM Calificaciones 
            WHERE id_pedido = p.id_pedido
            LIMIT 1
        ), 0) AS calificado
    FROM Pedidos p
    JOIN Restaurantes r ON p.id_restaurante = r.id_restaurante
    LEFT JOIN Repartidores rep ON p.id_repartidor = rep.id_repartidor
    LEFT JOIN Usuarios u ON rep.id_usuario = u.id_usuario
    WHERE p.id_pedido = p_id_pedido;
    
    -- Obtener combos del pedido
    SELECT 
        dp.id_detalle,
        c.id_combo,
        c.nombre AS nombre_combo,
        c.descripcion,
        dp.cantidad,
        dp.precio_unitario,
        (dp.cantidad * dp.precio_unitario) AS subtotal
    FROM Detalle_Pedidos dp
    JOIN Combos c ON dp.id_combo = c.id_combo
    WHERE dp.id_pedido = p_id_pedido;
END //
DELIMITER ;

-- pedidos de un cliente
DELIMITER //
CREATE PROCEDURE sp_listar_pedidos_cliente(
    IN p_id_cliente INT,
    IN p_estado VARCHAR(20) -- Opcional: filtrar por estado
)
BEGIN
    -- Validar que el cliente existe
    IF NOT EXISTS (SELECT 1 FROM Usuarios WHERE id_usuario = p_id_cliente AND tipo = 'cliente') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cliente no encontrado';
    END IF;
    
    -- Listar pedidos con filtro opcional por estado
    IF p_estado IS NULL OR p_estado = '' THEN
        SELECT 
            p.id_pedido,
            p.fecha_pedido,
            p.estado,
            p.total,
            r.nombre AS restaurante,
            r.tipo_comida,
            rep.id_repartidor,
            u.nombre AS nombre_repartidor,
            IFNULL((
                SELECT 1 FROM Calificaciones 
                WHERE id_pedido = p.id_pedido
                LIMIT 1
            ), 0) AS calificado
        FROM Pedidos p
        JOIN Restaurantes r ON p.id_restaurante = r.id_restaurante
        LEFT JOIN Repartidores rep ON p.id_repartidor = rep.id_repartidor
        LEFT JOIN Usuarios u ON rep.id_usuario = u.id_usuario
        WHERE p.id_cliente = p_id_cliente
        ORDER BY p.fecha_pedido DESC;
    ELSE
        SELECT 
            p.id_pedido,
            p.fecha_pedido,
            p.estado,
            p.total,
            r.nombre AS restaurante,
            r.tipo_comida,
            rep.id_repartidor,
            u.nombre AS nombre_repartidor,
            IFNULL((
                SELECT 1 FROM Calificaciones 
                WHERE id_pedido = p.id_pedido
                LIMIT 1
            ), 0) AS calificado
        FROM Pedidos p
        JOIN Restaurantes r ON p.id_restaurante = r.id_restaurante
        LEFT JOIN Repartidores rep ON p.id_repartidor = rep.id_repartidor
        LEFT JOIN Usuarios u ON rep.id_usuario = u.id_usuario
        WHERE p.id_cliente = p_id_cliente
        AND p.estado = p_estado
        ORDER BY p.fecha_pedido DESC;
    END IF;
END //
DELIMITER ;

-- calificar pedido
DELIMITER //
CREATE PROCEDURE sp_calificar_pedido(
    IN p_id_pedido INT,
    IN p_id_cliente INT,
    IN p_puntaje_repartidor INT,
    IN p_puntaje_restaurante INT,
    IN p_comentario TEXT,
    IN p_queja BOOLEAN
)
BEGIN
    DECLARE v_id_repartidor INT;
    DECLARE v_id_restaurante INT;
    DECLARE v_estado_pedido VARCHAR(20);
    DECLARE v_id_calificacion INT;
    
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 1. Validar que el pedido existe y pertenece al cliente
    SELECT id_repartidor, id_restaurante, estado 
    INTO v_id_repartidor, v_id_restaurante, v_estado_pedido
    FROM Pedidos
    WHERE id_pedido = p_id_pedido AND id_cliente = p_id_cliente;
    
    IF v_id_repartidor IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido no encontrado o no pertenece al cliente';
    END IF;
    
    -- 2. Validar que el pedido está entregado
    IF v_estado_pedido != 'entregado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Solo se pueden calificar pedidos entregados';
    END IF;
    
    -- 3. Validar que no está calificado ya
    IF EXISTS (SELECT 1 FROM Calificaciones WHERE id_pedido = p_id_pedido) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Este pedido ya fue calificado';
    END IF;
    
    -- 4. Validar puntajes
    IF p_puntaje_repartidor NOT BETWEEN 1 AND 5 OR p_puntaje_restaurante NOT BETWEEN 1 AND 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Los puntajes deben estar entre 1 y 5';
    END IF;
    
    -- 5. Insertar calificación
    INSERT INTO Calificaciones (
        id_pedido, id_cliente, id_repartidor,
        puntaje_repartidor, puntaje_restaurante,
        comentario, queja
    ) VALUES (
        p_id_pedido, p_id_cliente, v_id_repartidor,
        p_puntaje_repartidor, p_puntaje_restaurante,
        p_comentario, p_queja
    );
    
    SET v_id_calificacion = LAST_INSERT_ID();
    
    -- 6. Si es queja, registrar en tabla de quejas
    IF p_queja THEN
        INSERT INTO Quejas (id_calificacion, descripcion)
        VALUES (v_id_calificacion, IFNULL(p_comentario, 'Queja sin comentario'));
        
        -- Asignar amonestación al repartidor si la calificación es <= 2
        IF p_puntaje_repartidor <= 2 THEN
            CALL sp_asignar_amonestacion(v_id_repartidor);
        END IF;
    END IF;
    
    -- 7. Marcar repartidor como disponible nuevamente
    UPDATE Repartidores 
    SET estado_disponibilidad = 'disponible',
        distancia_pedido = 0
    WHERE id_repartidor = v_id_repartidor;
    
    COMMIT;
    
    -- Retornar éxito
    SELECT 1 AS success, 'Calificación registrada exitosamente' AS message;
END //
DELIMITER ;

-- ver detalle de pedido es especifico
call sp_ver_detalle_pedido_restaurante(22,5)
DELIMITER //
CREATE PROCEDURE sp_ver_detalle_pedido_restaurante(
    IN p_id_pedido INT,
    IN p_id_restaurante INT)
BEGIN
    -- Verificar que el pedido pertenece al restaurante
    DECLARE v_restaurante_pedido INT;
    
    SELECT id_restaurante INTO v_restaurante_pedido
    FROM Pedidos
    WHERE id_pedido = p_id_pedido;
    
    IF v_restaurante_pedido IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido no encontrado';
    END IF;
    
    IF v_restaurante_pedido != p_id_restaurante THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Este pedido no pertenece a su restaurante';
    END IF;
    
    -- Obtener información del pedido
    SELECT 
        p.id_pedido,
        p.fecha_pedido,
        p.estado,
        p.total,
        u.nombre AS nombre_cliente,
        u.direccion AS direccion_cliente,
        u.telefono,
        r.nombre AS nombre_repartidor,
        r.telefono AS telefono_repartidor
    FROM Pedidos p
    JOIN Usuarios u ON p.id_cliente = u.id_usuario
    LEFT JOIN Repartidores rep ON p.id_repartidor = rep.id_repartidor
    LEFT JOIN Usuarios r ON rep.id_usuario = r.id_usuario
    WHERE p.id_pedido = p_id_pedido;
    
    -- Obtener detalles del pedido
    SELECT 
        c.nombre AS combo,
        dp.cantidad,
        dp.precio_unitario,
        (dp.cantidad * dp.precio_unitario) AS subtotal
    FROM Detalle_Pedidos dp
    JOIN Combos c ON dp.id_combo = c.id_combo
    WHERE dp.id_pedido = p_id_pedido;
END //
DELIMITER ;

-- actualizar el estado 2
DELIMITER //
CREATE PROCEDURE sp_actualizar_estado_pedido2(
    IN p_id_pedido INT,
    IN p_nuevo_estado VARCHAR(20),
    IN p_tipo_usuario VARCHAR(20))
BEGIN
    DECLARE v_estado_actual VARCHAR(20);
    DECLARE v_id_repartidor INT;
    DECLARE v_id_restaurante INT;
    
    -- Obtener estado actual, repartidor y restaurante
    SELECT estado, id_repartidor, id_restaurante 
    INTO v_estado_actual, v_id_repartidor, v_id_restaurante
    FROM Pedidos
    WHERE id_pedido = p_id_pedido;
    
    -- Validar que el pedido existe
    IF v_estado_actual IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido no encontrado';
    END IF;
    
    -- Validar permisos según tipo de usuario
    IF p_tipo_usuario = 'restaurante' AND p_nuevo_estado NOT IN ('preparacion', 'en_camino') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Restaurante solo puede cambiar a "en preparación" o "en camino"';
    END IF;
    
    -- Validar transición de estados
    IF v_estado_actual = 'cancelado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido cancelado no puede cambiar de estado';
    END IF;
    
    IF v_estado_actual = 'entregado' AND p_nuevo_estado != 'entregado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido entregado no puede cambiar de estado';
    END IF;
    
    -- Actualizar estado del pedido
    UPDATE Pedidos
    SET estado = p_nuevo_estado
    WHERE id_pedido = p_id_pedido;
    
    -- Manejar diferentes estados del repartidor
    IF p_nuevo_estado = 'entregado' AND v_id_repartidor IS NOT NULL THEN
        -- Liberar repartidor y actualizar kilometraje
        UPDATE Repartidores
        SET estado_disponibilidad = 'disponible',
            distancia_pedido = 0,
            km_recorridos_diarios = km_recorridos_diarios + distancia_pedido
        WHERE id_repartidor = v_id_repartidor;
    
    ELSEIF p_nuevo_estado = 'en_camino' AND v_id_repartidor IS NOT NULL THEN
        -- Marcar repartidor como ocupado
        UPDATE Repartidores
        SET estado_disponibilidad = 'ocupado'
        WHERE id_repartidor = v_id_repartidor;
    
    ELSEIF p_nuevo_estado = 'cancelado' AND v_id_repartidor IS NOT NULL THEN
        -- Liberar repartidor
        UPDATE Repartidores
        SET estado_disponibilidad = 'disponible',
            distancia_pedido = 0
        WHERE id_repartidor = v_id_repartidor;
    END IF;
    
    SELECT 1 AS success, CONCAT('Estado actualizado a: ', p_nuevo_estado) AS message;
END //
DELIMITER ;

-- actualizar estado de pedido
DELIMITER //
CREATE PROCEDURE sp_actualizar_estado_pedido(
    IN p_id_pedido INT,
    IN p_nuevo_estado VARCHAR(20)
)
BEGIN
    DECLARE v_estado_actual VARCHAR(20);
    DECLARE v_id_repartidor INT;
    
    -- Obtener estado actual y repartidor
    SELECT estado, id_repartidor INTO v_estado_actual, v_id_repartidor
    FROM Pedidos
    WHERE id_pedido = p_id_pedido;
    
    -- Validar que el pedido existe
    IF v_estado_actual IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido no encontrado';
    END IF;
    
    -- Validar transición de estados
    IF v_estado_actual = 'cancelado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido cancelado no puede cambiar de estado';
    END IF;
    
    IF v_estado_actual = 'entregado' AND p_nuevo_estado != 'entregado' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Pedido entregado no puede cambiar de estado';
    END IF;
    
    -- Actualizar estado del pedido
    UPDATE Pedidos
    SET estado = p_nuevo_estado
    WHERE id_pedido = p_id_pedido;
    
    -- Manejar diferentes estados del repartidor
    IF p_nuevo_estado = 'entregado' AND v_id_repartidor IS NOT NULL THEN
        -- Liberar repartidor y actualizar kilometraje
        UPDATE Repartidores
        SET estado_disponibilidad = 'disponible',
            distancia_pedido = 0,
            km_recorridos_diarios = km_recorridos_diarios + distancia_pedido
        WHERE id_repartidor = v_id_repartidor;
    
    ELSEIF p_nuevo_estado = 'en_camino' AND v_id_repartidor IS NOT NULL THEN
        -- Marcar repartidor como ocupado
        UPDATE Repartidores
        SET estado_disponibilidad = 'ocupado'
        WHERE id_repartidor = v_id_repartidor;
    
    ELSEIF p_nuevo_estado = 'cancelado' AND v_id_repartidor IS NOT NULL THEN
        -- Liberar repartidor
        UPDATE Repartidores
        SET estado_disponibilidad = 'disponible',
            distancia_pedido = 0
        WHERE id_repartidor = v_id_repartidor;
    END IF;
    
    SELECT 1 AS success, CONCAT('Estado actualizado a: ', p_nuevo_estado) AS message;
END //
DELIMITER ;

select * from pedidos;
select * from repartidores;
select * from restaurantes;

CALL sp_listar_pedidos_cliente(21, null); -- Todos los pedidos

-- procedimiento para listar pedido de ese retaurante 
call sp_listar_pedidos_restaurante(5,'pendiente')

DELIMITER //
CREATE PROCEDURE sp_listar_pedidos_restaurante(
    IN p_id_restaurante INT,
    IN p_estado VARCHAR(20))
BEGIN
    SELECT 
        p.id_pedido,
        p.fecha_pedido,
        p.estado,
        p.total,
        u.nombre AS nombre_cliente,
        u.direccion AS direccion_cliente,
        r.nombre AS nombre_repartidor,
        COUNT(dp.id_detalle) AS cantidad_items
    FROM Pedidos p
    JOIN Usuarios u ON p.id_cliente = u.id_usuario
    LEFT JOIN Repartidores rep ON p.id_repartidor = rep.id_repartidor
    LEFT JOIN Usuarios r ON rep.id_usuario = r.id_usuario
    JOIN Detalle_Pedidos dp ON p.id_pedido = dp.id_pedido
    WHERE p.id_restaurante = p_id_restaurante
    AND (p_estado IS NULL OR p.estado = p_estado)
    GROUP BY p.id_pedido
    ORDER BY p.fecha_pedido DESC;
END //
DELIMITER ;

-- procedimiento para verificar su un usurio existe por cedula
use foodorders;

select * from calificaciones;
select * from usuarios;
select * from pedidos;
select * from repartidores;
select * from restaurantes;

call sp_verificar_usuario(7777)
drop procedure sp_verificar_usuario

DELIMITER //

CREATE PROCEDURE sp_verificar_usuario(
    IN p_cedula VARCHAR(20)
)
BEGIN
    -- Verificar usuario normal
    SELECT 
        id_usuario,  
        nombre,
        tipo,
        estado,
        'usuario' AS origen
    FROM Usuarios 
    WHERE cedula = p_cedula AND tipo != 'repartidor'

    UNION

    -- Verificar restaurante
    SELECT 
        id_restaurante AS id_usuario,  
        nombre,
        'restaurante' AS tipo,
        IF(activo, 'activo', 'suspendido') AS estado,
        'restaurante' AS origen
    FROM Restaurantes 
    WHERE cedula_juridica = p_cedula

    UNION

    -- Verificar repartidor (buscando por cédula en Usuarios y relacionando con Repartidores)
    SELECT 
        r.id_repartidor AS id_usuario,  -- Ahora devuelve el id_repartidor
        u.nombre,
        u.tipo,
        CASE 
            WHEN r.estado_disponibilidad = 'ocupado' THEN 'ocupado'
            ELSE 'activo'
        END AS estado,
        'repartidor' AS origen
    FROM Usuarios u
    JOIN Repartidores r ON u.id_usuario = r.id_usuario
    WHERE u.cedula = p_cedula AND u.tipo = 'repartidor'

    LIMIT 1;
END //

DELIMITER ;


-- obtener los restaurantes 
DELIMITER //
CREATE PROCEDURE sp_obtener_restaurantes(
)
BEGIN
    SELECT * FROM Restaurantes; 
END //
DELIMITER ;

-- obtener informacion del cliente por id
DELIMITER //
CREATE PROCEDURE sp_obtener_cliente(
    IN p_id_usuario INT
)
BEGIN
    SELECT * FROM Usuarios 
    WHERE id_usuario = p_id_usuario AND tipo = 'cliente';
END //
DELIMITER ;

-- registrar restaurante
DELIMITER //
CREATE PROCEDURE sp_registrar_restaurante(
    IN p_nombre VARCHAR(100),
    IN p_cedula_juridica VARCHAR(20),
    IN p_direccion TEXT,
    IN p_tipo_comida VARCHAR(50)
)
BEGIN
    DECLARE restaurant_count INT;
    
    -- Verificar si la cédula jurídica ya existe
    SELECT COUNT(*) INTO restaurant_count FROM Restaurantes WHERE cedula_juridica = p_cedula_juridica;
    
    IF restaurant_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ya existe un restaurante con esta cédula jurídica';
    ELSE
        -- Insertar nuevo restaurante
        INSERT INTO Restaurantes (nombre, cedula_juridica, direccion, tipo_comida)
        VALUES (p_nombre, p_cedula_juridica, p_direccion, p_tipo_comida);
        
        SELECT LAST_INSERT_ID() AS id_restaurante;
    END IF;
END //
DELIMITER ; 


-- registrar repartidores
drop procedure sp_registrar_repartidor
DELIMITER //
CREATE PROCEDURE sp_registrar_repartidor(
    IN p_id_usuario INT,
    IN p_costo_por_km DECIMAL(10,2)
)
BEGIN
    -- Verificar que el usuario existe y es de tipo repartidor
    IF NOT EXISTS (SELECT 1 FROM Usuarios WHERE id_usuario = p_id_usuario AND tipo = 'repartidor') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El usuario no existe o no es de tipo repartidor';
    ELSE
        -- Insertar repartidor
        INSERT INTO Repartidores (id_usuario, costo_por_km)
        VALUES (p_id_usuario, p_costo_por_km);
    END IF;
END //
DELIMITER ;

-- Actualizar estado repartidor
DELIMITER //
CREATE PROCEDURE sp_actualizar_estado_repartidor(
    IN p_id_repartidor INT,
    IN p_estado ENUM('disponible', 'ocupado')
)
BEGIN
    UPDATE Repartidores 
    SET estado_disponibilidad = p_estado 
    WHERE id_repartidor = p_id_repartidor;
END //
DELIMITER ;

-- obtener combo de retaurante en especifico
call sp_obtener_combos(1)
DELIMITER //
CREATE PROCEDURE sp_obtener_combos(
    IN p_id_restaurante INT
)
BEGIN
    SELECT 
        id_combo,
        id_restaurante,
        numero_combo,
        nombre,
        descripcion,
        precio
    FROM Combos
    WHERE id_restaurante = p_id_restaurante AND activo = TRUE;
END //
DELIMITER ;

-- Asignar amonestación a repartidor
DELIMITER //
CREATE PROCEDURE sp_asignar_amonestacion(
    IN p_id_repartidor INT
)
BEGIN
    DECLARE v_amonestaciones INT;
    
    UPDATE Repartidores 
    SET amonestaciones = amonestaciones + 1 
    WHERE id_repartidor = p_id_repartidor;
    
    SELECT amonestaciones INTO v_amonestaciones 
    FROM Repartidores 
    WHERE id_repartidor = p_id_repartidor;
    
    -- Si tiene 4 o más amonestaciones, suspender al usuario
    IF v_amonestaciones >= 4 THEN
        UPDATE Usuarios u
        JOIN Repartidores r ON u.id_usuario = r.id_usuario
        SET u.estado = 'suspendido'
        WHERE r.id_repartidor = p_id_repartidor;
    END IF;
    
    SELECT v_amonestaciones AS amonestaciones_actuales;
END //
DELIMITER ;

Select * from Restaurantes
Delete from Restaurantes where id_restaurante = 4

-- Registrar restaurante
DELIMITER //
CREATE PROCEDURE sp_registrar_restaurante(
    IN p_nombre VARCHAR(100),
    IN p_cedula_juridica VARCHAR(20),
    IN p_direccion TEXT,
    IN p_tipo_comida VARCHAR(50)
)
BEGIN
    DECLARE rest_count INT;
    
    -- Verificar si la cédula jurídica ya existe
    SELECT COUNT(*) INTO rest_count FROM Restaurantes WHERE cedula_juridica = p_cedula_juridica;
    
    IF rest_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ya existe un restaurante con esta cédula jurídica';
    ELSE
        -- Insertar nuevo restaurante
        INSERT INTO Restaurantes (nombre, cedula_juridica, direccion, tipo_comida)
        VALUES (p_nombre, p_cedula_juridica, p_direccion, p_tipo_comida);
        
        SELECT LAST_INSERT_ID() AS id_restaurante;
    END IF;
END //
DELIMITER ;

select * from Restaurantes
select * from combos

-- Registrar combo para restaurante
DELIMITER //
CREATE PROCEDURE sp_registrar_combo(
    IN p_id_restaurante INT,
    IN p_numero_combo INT,
    IN p_nombre VARCHAR(100),
    IN p_descripcion TEXT
)
BEGIN
    DECLARE v_precio DECIMAL(10,2);
    
    -- Calcular precio según número de combo
    SET v_precio = 4000.00 + ((p_numero_combo - 1) * 1000.00);
    
    -- Insertar combo
    INSERT INTO Combos (id_restaurante, numero_combo, nombre, descripcion, precio)
    VALUES (p_id_restaurante, p_numero_combo, p_nombre, p_descripcion, v_precio);
    
    SELECT LAST_INSERT_ID() AS id_combo;
END //
DELIMITER ;

-- pruebas de app
select * from repartidores;
select * from restaurantes;
select * from usuarios;
select * from pedidos;
select * from Detalle_Pedidos;
-- fin de pruebas


-- clientes y sus pedidos
CALL sp_clientes_y_mayor_pedidos();

DELIMITER $$
CREATE PROCEDURE sp_clientes_y_mayor_pedidos()
BEGIN
    -- Parte 1: Listado de pedidos por cliente
    SELECT 
        u.id_usuario,
        u.nombre AS nombre_cliente,
        u.correo AS correo_cliente,
        p.id_pedido,
        p.fecha_pedido,
        p.total
    FROM Usuarios u
    INNER JOIN Pedidos p ON u.id_usuario = p.id_cliente
    WHERE u.tipo = 'cliente'
    ORDER BY u.id_usuario, p.fecha_pedido ASC;

    -- Parte 2: Cliente con mayor cantidad de pedidos
    SELECT 
        u.id_usuario,
        u.nombre AS nombre_cliente,
        u.correo AS correo_cliente,
        COUNT(p.id_pedido) AS cantidad_pedidos
    FROM Usuarios u
    INNER JOIN Pedidos p ON u.id_usuario = p.id_cliente
    WHERE u.tipo = 'cliente'
    GROUP BY u.id_usuario, u.nombre, u.correo
    ORDER BY cantidad_pedidos DESC
    LIMIT 1;
END$$
DELIMITER ;

-- Crear nuevo pedido
DELIMITER //
CREATE PROCEDURE sp_crear_pedido(
    IN p_id_cliente INT,
    IN p_id_restaurante INT,
    IN p_direccion_entrega TEXT
)
BEGIN
    DECLARE v_id_pedido INT;
    DECLARE v_id_repartidor INT;
    DECLARE v_distancia DECIMAL(10,2);
    DECLARE v_costo_transporte DECIMAL(10,2);
    DECLARE v_subtotal DECIMAL(10,2) DEFAULT 0.00;
    DECLARE v_iva DECIMAL(10,2);
    DECLARE v_total DECIMAL(10,2);

    -- Buscar repartidor disponible más cercano (simplificado)
    SELECT r.id_repartidor, r.distancia_pedido
    INTO v_id_repartidor, v_distancia
    FROM Repartidores r
    JOIN Usuarios u ON r.id_usuario = u.id_usuario
    WHERE r.estado_disponibilidad = 'disponible' AND u.estado = 'activo'
    ORDER BY r.km_recorridos_diarios ASC
    LIMIT 1;

    -- Calcular costo de transporte con tarifa fija de 500 por km
    SET v_costo_transporte = v_distancia * 500;

    -- Calcular IVA (13% de subtotal + costo_transporte)
    SET v_iva = ROUND((v_subtotal + v_costo_transporte) * 0.13, 2);

    -- Calcular total
    SET v_total = v_subtotal + v_costo_transporte + v_iva;

    -- Crear pedido
    INSERT INTO Pedidos (
        id_cliente, id_restaurante, id_repartidor,
        direccion_entrega, estado,
        subtotal, costo_transporte, iva, total
    )
    VALUES (
        p_id_cliente, p_id_restaurante, v_id_repartidor,
        p_direccion_entrega, 'pendiente',
        v_subtotal, v_costo_transporte, v_iva, v_total
    );

    SET v_id_pedido = LAST_INSERT_ID();

    -- Marcar repartidor como ocupado
    IF v_id_repartidor IS NOT NULL THEN
        UPDATE Repartidores SET estado_disponibilidad = 'ocupado'
        WHERE id_repartidor = v_id_repartidor;
    END IF;

    -- Retornar datos clave
    SELECT
        v_id_pedido AS id_pedido,
        v_id_repartidor AS id_repartidor,
        v_distancia AS distancia_km,
        v_costo_transporte AS costo_transporte,
        v_iva AS iva,
        v_total AS total;
END //
DELIMITER ;

-- Agregar combo a pedido
DELIMITER //
CREATE PROCEDURE sp_agregar_combo_pedido(
    IN p_id_pedido INT,
    IN p_id_combo INT,
    IN p_cantidad INT
)
BEGIN
    DECLARE v_precio_unitario DECIMAL(10,2);
    
    -- Obtener precio del combo
    SELECT precio INTO v_precio_unitario FROM Combos WHERE id_combo = p_id_combo;
    
    -- Insertar detalle
    INSERT INTO Detalle_Pedidos (id_pedido, id_combo, cantidad, precio_unitario)
    VALUES (p_id_pedido, p_id_combo, p_cantidad, v_precio_unitario);
    
    -- Actualizar subtotal del pedido
    UPDATE Pedidos p
    JOIN (
        SELECT id_pedido, SUM(cantidad * precio_unitario) AS subtotal
        FROM Detalle_Pedidos
        WHERE id_pedido = p_id_pedido
        GROUP BY id_pedido
    ) dp ON p.id_pedido = dp.id_pedido
    SET p.subtotal = dp.subtotal;
    
    SELECT LAST_INSERT_ID() AS id_detalle;
END //
DELIMITER ;

-- Finalizar pedido (calcular totales)
DELIMITER //
CREATE PROCEDURE sp_finalizar_pedido(
    IN p_id_pedido INT
)
BEGIN
    DECLARE v_subtotal DECIMAL(10,2);
    DECLARE v_costo_transporte DECIMAL(10,2);
    DECLARE v_iva DECIMAL(10,2);
    DECLARE v_total DECIMAL(10,2);
    DECLARE v_id_repartidor INT;
    
    -- Obtener subtotal y repartidor
    SELECT subtotal, id_repartidor INTO v_subtotal, v_id_repartidor
    FROM Pedidos WHERE id_pedido = p_id_pedido;
    
    -- Calcular costo de transporte (simplificado: 500 colones por km)
    SELECT 500 * distancia_pedido INTO v_costo_transporte
    FROM Repartidores WHERE id_repartidor = v_id_repartidor;
    
    -- Calcular IVA (13%)
    SET v_iva = (v_subtotal + v_costo_transporte) * 0.13;
    
    -- Calcular total
    SET v_total = v_subtotal + v_costo_transporte + v_iva;
    
    -- Actualizar pedido
    UPDATE Pedidos
    SET costo_transporte = v_costo_transporte,
        iva = v_iva,
        total = v_total,
        estado = 'preparacion'
    WHERE id_pedido = p_id_pedido;
    
    SELECT v_subtotal AS subtotal, v_costo_transporte AS transporte, v_iva AS iva, v_total AS total;
END //
DELIMITER ;

--  Procedimientos para Reportes
DELIMITER //
CREATE PROCEDURE sp_reporte_restaurantes_pedidos(
    IN p_tipo ENUM('mayor', 'menor')
)
BEGIN
    IF p_tipo = 'mayor' THEN
        -- Restaurante con mayor número de pedidos
        SELECT r.id_restaurante, r.nombre, COUNT(p.id_pedido) AS total_pedidos
        FROM Restaurantes r
        LEFT JOIN Pedidos p ON r.id_restaurante = p.id_restaurante
        GROUP BY r.id_restaurante, r.nombre
        ORDER BY total_pedidos DESC
        LIMIT 1;
    ELSE
        -- Restaurante con menor número de pedidos
        SELECT r.id_restaurante, r.nombre, COUNT(p.id_pedido) AS total_pedidos
        FROM Restaurantes r
        LEFT JOIN Pedidos p ON r.id_restaurante = p.id_restaurante
        GROUP BY r.id_restaurante, r.nombre
        ORDER BY total_pedidos ASC
        LIMIT 1;
    END IF;
END //
DELIMITER ;


-- Reporte de clientes (activos/suspendidos)
DELIMITER //
CREATE PROCEDURE sp_reporte_clientes(
    IN p_estado ENUM('activo', 'suspendido')
)
BEGIN
    SELECT id_usuario, cedula, nombre
    FROM Usuarios
    WHERE tipo = 'cliente' AND estado = p_estado;
END //
DELIMITER ;

-- obtener todos los usuarios
DELIMITER //
CREATE PROCEDURE sp_GetAllclientes()
BEGIN
    SELECT * FROM Usuarios
    WHERE tipo = 'cliente';
END //
DELIMITER ;


-- obtener todos los repartidores
DELIMITER //
CREATE PROCEDURE getAllRepartidores()
BEGIN
    SELECT 
        r.id_repartidor,
        r.id_usuario,
        u.nombre,
        u.cedula,
        u.estado AS estado_usuario,
        r.estado_disponibilidad,
        r.distancia_pedido,
        r.km_recorridos_diarios,
        r.costo_por_km,
        r.amonestaciones
    FROM 
        Repartidores r
    INNER JOIN 
        Usuarios u ON r.id_usuario = u.id_usuario;
END //
DELIMITER ;

call getAllRepartidores

-- Reporte de repartidores con cero amonestaciones
DELIMITER //
CREATE PROCEDURE sp_reporte_repartidores_cero_amonestaciones()
BEGIN
    SELECT 
        r.id_repartidor,
        u.cedula,
        u.nombre,
        r.amonestaciones
    FROM Repartidores r
    JOIN Usuarios u ON r.id_usuario = u.id_usuario
    WHERE r.amonestaciones = 0;
END //
DELIMITER ;


-- Reporte de quejas por repartidor
DELIMITER //
CREATE PROCEDURE sp_reporte_quejas_repartidores()
BEGIN
    SELECT 
        r.id_repartidor,
        u.nombre AS nombre_repartidor,
        COUNT(q.id_queja) AS total_quejas
    FROM Repartidores r
    JOIN Usuarios u ON r.id_usuario = u.id_usuario
    LEFT JOIN Calificaciones c ON r.id_repartidor = c.id_repartidor
    LEFT JOIN Quejas q ON c.id_calificacion = q.id_calificacion
    GROUP BY r.id_repartidor, u.nombre
    ORDER BY total_quejas DESC;
END //
DELIMITER ;

CALL sp_registrar_usuario('101010101', 'Juan Pérez', 'juan@example.com', 'Calle 123', '8888-8888', '1111222233334444', 'cliente');
CALL sp_registrar_usuario('202020202', 'Carlos Gómez', 'carlos@example.com', 'Avenida Central', '8999-9999', '5555666677778888', 'repartidor');


CALL sp_registrar_repartidor(2, 150.00);


CALL sp_registrar_restaurante('Papa Johns', '3101123456', 'Barrio La Paz', 'Italiana');
CALL sp_registrar_restaurante('Sushi Ya', '3101654321', 'Centro Comercial Este', 'Japonesa');
call sp_obtener_restaurantes();

CALL sp_registrar_combo(1, 1, 'Combo Familiar', 'Pizza grande + 2 refrescos');
CALL sp_registrar_combo(1, 2, 'Combo Personal', 'Pizza pequeña + refresco');
CALL sp_registrar_combo(2, 1, 'Sushi Deluxe', '12 piezas variadas + bebida');

CALL sp_crear_pedido(1, 1, 'Casa de Juan, Calle 123, San José');

INSERT INTO Usuarios (
    cedula, nombre, correo, direccion, telefono, numero_tarjeta, tipo
) VALUES (
    '1111', 'Manuel', 'admin@foodorders.com', 'Oficina Central', '62567524', '110100101', 'admin'
);

select * from Usuarios

