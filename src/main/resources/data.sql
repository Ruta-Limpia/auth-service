-- Usuarios semilla con id fijo: flota y rutas usan estos ids como conductor_id y vecino_id.
-- La contraseña de los tres es 123456. Solo para desarrollo y la demo; se desactiva con SEED_DATA=never.
INSERT INTO usuarios (id, nombre, email, password_hash, rol, activo, fecha_creacion) VALUES
    (1, 'Admin RutaLimpia', 'admin@rutalimpia.cl', '$2a$10$hcRsVBzgZrob5IS8ocSjkO3PtXHrnQKO8F/7KslCZ9ySNFMucYsTq', 'ADMIN', true, NOW()),
    (2, 'Carlos Conductor', 'conductor@rutalimpia.cl', '$2a$10$U0nUhUjZPTrnKJ6X/YZN2OcfJJboDkXwipei3BRTaxjcsDj3K4dqy', 'CONDUCTOR', true, NOW()),
    (3, 'Valentina Vecina', 'vecino@rutalimpia.cl', '$2a$10$c3QmUhiHTOHNjOQw12WkKelNcm0id0XVrvGyeYHy.5YSLA5wXxeOq', 'VECINO', true, NOW())
ON DUPLICATE KEY UPDATE id = id;
