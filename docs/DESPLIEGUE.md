# Despliegue en producción con HTTPS

El requisito de la arquitectura dice que el cliente envía el PDF **cifrado por HTTPS**. Predia no termina TLS por sí mismo: Spring Boot escucha en HTTP (puerto 8080) y el cifrado lo pone un **proxy inverso** delante, que además sirve el frontend compilado. En desarrollo (`./arrancar.sh`) todo va por `http://localhost` y no hay cifrado: eso es solo para probar en tu equipo.

> Esta guía **no se ha probado en un servidor real**; es el camino recomendado y está escrita para que se pueda seguir y verificar. Usa un dominio propio y un servidor con los puertos 80 y 443 abiertos.

## Por qué importa

- La sesión es un JWT que el navegador guarda y envía en cada petición: sin HTTPS, cualquiera en la misma red podría verlo.
- Los PDF (contratos, escrituras, certificados) y los datos de clientes (cédula, ingresos) viajan en esas peticiones.

## 1. Compilar el frontend

```bash
cd frontend && npm ci && npm run build      # deja los archivos estáticos en frontend/dist
```

## 2. Arrancar el backend (solo accesible desde el propio servidor)

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home   # en Linux: la ruta de tu JDK 21
export SERVER_FORWARD_HEADERS_STRATEGY=framework    # confía en las cabeceras X-Forwarded-* del proxy
export DEMO_HABILITADA=false                        # sin los datos ficticios
java -jar target/predia-1.2.0.jar                   # tras ./mvnw -DskipTests package
```

### El primer administrador

Con la demo apagada no existe ningún usuario. Antes del primer arranque define:

```bash
export PREDIA_URL_PUBLICA=https://predia.tu-dominio.com
export PREDIA_ADMIN_EMAIL=administracion@tu-inmobiliaria.co
export PREDIA_ADMIN_NOMBRE="Nombre del administrador"
export PREDIA_INMOBILIARIA_NOMBRE="Tu Inmobiliaria"
export PREDIA_INMOBILIARIA_NIT=900123456
```

Al arrancar, Predia crea la inmobiliaria y ese administrador **sin contraseña**, y escribe **una sola vez** en el log el enlace para crearla (vale 72 horas). Después, desde **Usuarios**, ese administrador invita al resto del personal. Si ya existe un ADMIN, no hace nada. Ninguna contraseña viaja en variables de entorno.

Para que las invitaciones lleguen por correo hay que apuntar `MAIL_HOST`/`MAIL_PORT` a un servidor SMTP real y configurar sus credenciales y TLS con las propiedades `spring.mail.*` de Spring Boot (no se probó con un proveedor real). Si el correo falla, quien invita puede copiar el enlace desde la pantalla.

Variables obligatorias en producción: `JWT_SECRET` (largo y aleatorio), `DB_*` y, si se usa IA, `OPENAI_API_KEY`. No expongas el puerto 8080 ni el de PostgreSQL a internet: solo el proxy.

## 3. Proxy con certificado automático (Caddy)

Con [Caddy](https://caddyserver.com) el certificado de Let's Encrypt se pide y se renueva solo. Un `Caddyfile` mínimo:

```caddyfile
predia.tu-dominio.com {
    encode gzip

    # La API y los PDF pasan al backend (mismo servidor, puerto 8080).
    handle /api/* {
        request_body {
            max_size 21MB
        }
        reverse_proxy localhost:8080
    }

    # Todo lo demás es el frontend compilado; las rutas de React caen en index.html.
    handle {
        root * /ruta/a/predia/frontend/dist
        try_files {path} /index.html
        file_server
    }

    header Strict-Transport-Security "max-age=31536000"
}
```

`21MB` coincide con `spring.servlet.multipart.max-request-size` (los PDF pueden pesar hasta 20 MB). Si prefieres nginx, el esquema es el mismo: `listen 443 ssl`, `location /api/` con `proxy_pass http://127.0.0.1:8080` y `proxy_set_header X-Forwarded-Proto https`.

## 4. Comprobar

```bash
curl -I https://predia.tu-dominio.com/                      # la página de ingreso, por HTTPS (200)
curl -I https://predia.tu-dominio.com/api/inmuebles        # llega al backend: 401 sin sesión es lo esperado
curl -I http://predia.tu-dominio.com                       # Caddy redirige a https
```

Y revisa el candado del navegador en la pantalla de ingreso.
