# Guía de Configuración CORS - Solución de Errores de Conexión

## Problema Identificado
La configuración original de CORS solo permitía peticiones desde `https://conecta2-backend-2.onrender.com` (la URL del backend), bloqueando todas las peticiones del frontend.

## Cambios Realizados

### 1. ✅ Actualización de `CorsConfig.java`
- **Antes**: Solo permitía un origen: `https://conecta2-backend-2.onrender.com`
- **Después**: Permite múltiples orígenes configurables mediante variables de entorno

**Cambios:**
- Agregado soporte para variable de entorno `CORS_ALLOWED_ORIGINS`
- Valores por defecto para desarrollo local:
  - `http://localhost:3000`
  - `http://localhost:3001`
  - `http://localhost:4200`
  - `http://127.0.0.1:3000`
  - `http://127.0.0.1:3001`
  - `http://127.0.0.1:4200`

- Agregado soporte para método `HEAD`
- Expuestos headers adicionales: `X-Total-Count`, `X-Content-Type-Options`

### 2. ✅ Actualización de `application.properties`
- Agregada configuración: `cors.allowed-origins`
- Actualizado `app.base-url` para desarrollo local

### 3. ✅ Mejora en `JwtAuthenticationFilter.java`
- Agregado filtro explícito para peticiones OPTIONS
- Las peticiones OPTIONS (CORS preflight) ahora se permiten sin autenticación
- Esto asegura que los navegadores puedan verificar permisos CORS correctamente

## Instrucciones de Configuración

### Para Desarrollo Local
No necesitas hacer nada adicional. El backend está configurado para aceptar peticiones desde:
- `http://localhost:3000`
- `http://localhost:3001`
- `http://localhost:4200`

### Para Producción
Debes configurar la variable de entorno `CORS_ALLOWED_ORIGINS` con tu URL de frontend.

**Ejemplo en Render (o tu platform de hosting):**
```
CORS_ALLOWED_ORIGINS=https://tu-frontend.vercel.app,https://tu-frontend.netlify.app
```

## Configuración del Frontend

Para conectarte correctamente al backend, usa:

### 1. **Configuración Base URL**
```javascript
// Para desarrollo local
const API_BASE_URL = 'http://localhost:8080/api/v1';

// Para producción
const API_BASE_URL = 'https://conecta2-backend-2.onrender.com/api/v1';
```

### 2. **Configuración de Axios/Fetch**
```javascript
// Con Axios
const instance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // Importante para enviar cookies/credentials
});

// Con Fetch
fetch(`${API_BASE_URL}/auth/login`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include', // Importante para CORS con credenciales
  body: JSON.stringify(data)
})
```

### 3. **Headers Importantes**
- `Authorization: Bearer <token>` - Para peticiones autenticadas
- `Content-Type: application/json` - Para request/response JSON

## Headers CORS Soportados

**Origen (Origin):**
- Localhost en desarrollo
- Tu dominio del frontend en producción

**Métodos permitidos:**
- GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD

**Headers permitidos:**
- Todos (`*`)

**Headers expuestos al cliente:**
- `Authorization` - Para recibir tokens JWT
- `Content-Type` - Para información del contenido
- `X-Total-Count` - Para paginación
- `X-Content-Type-Options` - Para seguridad

## Endpoints Públicos (sin autenticación)
```
GET  /api/v1/clients/services/public/**
POST /api/v1/auth/**
GET  /api/Clientes/**
GET  /api/Trabajadores/**
POST /payment/webhook
```

## Endpoints Protegidos (requieren JWT)
```
POST /api/v1/clients/services/**
POST /api/v1/workers/services/**
GET  /api/v1/clients/*/offers/pending
POST /api/v1/offers/*/respond
POST /payment/**
```

## Verificación de la Configuración

Para verificar que CORS está funcionando correctamente:

### 1. **Usa una petición OPTIONS (preflight)**
```bash
curl -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -v
```

Deberías ver en la respuesta:
```
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD
```

### 2. **Verifica en el navegador**
Abre la consola del navegador (F12) y busca errores de CORS. No deberías ver mensajes como:
```
Access to XMLHttpRequest from origin 'http://localhost:3000' blocked by CORS policy
```

## Troubleshooting

### Error: "CORS policy blocked request"
1. Verifica que la URL de tu frontend esté en la lista `cors.allowed-origins`
2. Asegúrate que el frontend envíe el header `Origin` correcto
3. Reinicia el backend después de cambiar la configuración

### Error: "Token inválido"
1. Asegúrate de enviar el token en el header `Authorization: Bearer <token>`
2. Verifica que el token no haya expirado
3. Comprueba que el backend pueda validar el token

### Error: "Unauthorized" en peticiones autenticadas
1. Verifica que incluyas el header `Authorization` con el token JWT
2. El token debe estar en el formato `Bearer <token>`
3. Comprueba que estés usando la ruta correcta de autenticación

## Variables de Entorno Importantes

```bash
# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:3001

# Base URL
APP_BASE_URL=http://localhost:8080

# JWT
app.jwt.secret=tu_secret_aqui
app.jwt.access.exp-min=60

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=usuario
SPRING_DATASOURCE_PASSWORD=password

# Email
SPRING_MAIL_USERNAME=tu_email@gmail.com
SPRING_MAIL_PASSWORD=tu_contraseña_app

# Stripe
STRIPE_API_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...

# Google Maps
GOOGLE_MAPS_API_KEY=tu_key_aqui
```

## Próximos Pasos

1. ✅ Compila el backend con los cambios
2. Inicia el servidor: `./mvnw.cmd spring-boot:run`
3. Verifica que está escuchando en `http://localhost:8080`
4. Implementa la configuración en tu frontend
5. Prueba las peticiones desde el navegador
6. Verifica en la consola del navegador que no hay errores CORS

## Cambios Realizados en Detalle

### Archivo: `src/main/java/com/example/worker_registry/config/CorsConfig.java`
- Agregada inyección de propiedades con `@Value`
- Soporte para variable de entorno `CORS_ALLOWED_ORIGINS`
- Parseo dinámico de orígenes permitidos
- Agregados métodos HEAD
- Headers adicionales expuestos

### Archivo: `src/main/resources/application.properties`
- Nueva propiedad: `cors.allowed-origins`
- Actualizado `app.base-url`
- Comentarios explicativos

### Archivo: `src/main/java/com/example/worker_registry/securtity/JwtAuthenticationFilter.java`
- Agregado check explícito para método OPTIONS
- Las peticiones OPTIONS se procesan sin validación de JWT
- Mejora en la robustez del filtro

---

**Última actualización:** 2026-03-12
**Backend versión:** Spring Boot 3.x
