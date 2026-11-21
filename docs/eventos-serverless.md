# Cola SQS, Lambda y API Gateway

En esta parte agregamos un aviso por correo cuando alguien se equivoca 3 veces seguidas con la clave de una cuenta. Puede ser el mismo usuario que se olvidó la clave, o alguien intentando adivinarla, así que nos pareció útil que el administrador se entere.

Para hacerlo usamos lo que vimos en clases: una cola SQS, una función Lambda y SNS para el correo. También pusimos un API Gateway delante de auth-service, para que la API se use por HTTPS.

Todo se crea con Serverless Framework, igual que en el ejemplo del profe. El archivo es `lambda-alertas/serverless.yml` y lo despliega el pipeline cuando hay un push a `main`.

## Cómo funciona

```
cliente ──HTTPS──> API Gateway ──> auth-service (Swarm) ──> MySQL
                                        │
                                        │ login fallido / usuario nuevo
                                        v
                                   cola SQS ──(si falla 3 veces)──> DLQ
                                        │
                                        v
                                 Lambda "alertas"
                                        │ 3 fallidos seguidos
                                        v
                                   SNS ──> correo
```

1. Cuando un usuario se equivoca de clave, auth-service guarda el intento en `registros_acceso` (eso ya lo teníamos) y además manda un mensaje a la cola. En el mensaje va cuántos intentos fallidos seguidos lleva la cuenta en los últimos 15 minutos. Cuando alguien se registra, también manda un mensaje.
2. La cola guarda los mensajes hasta que la Lambda los lee.
3. La Lambda revisa cada mensaje. Si la cuenta llegó a 3 fallidos seguidos (o 6, 9...), publica en SNS. Los registros solo los anota en el log.
4. SNS manda el correo a quien esté suscrito.

Un mensaje se ve así (no lleva la clave ni el token):

```json
{
  "id": "6b0f0c8e-5d0e-4a8f-9a57-3c1f1f0c2a11",
  "tipo": "LOGIN_FALLIDO",
  "usuarioId": 12,
  "email": "vecino@rutalimpia.cl",
  "ip": "10.0.0.2",
  "motivo": "CLAVE_INCORRECTA",
  "fallidosSeguidos": 3,
  "fecha": "2026-11-21T15:00:00Z"
}
```

## Dónde está cada cosa

- `src/main/java/.../evento/`: el evento y la clase que lo manda a SQS
- `src/main/java/.../config/EventosConfig.java`: si no hay cola configurada (local, tests), los eventos no se mandan
- `AuthServiceImpl`: ahí se publican los eventos, en `registrar` y en `login`
- `RegistroAccesoServiceImpl.contarFallidosSeguidos`: cuenta los fallidos
- `lambda-alertas/`: la Lambda (es otro proyecto Maven, aparte)
- `lambda-alertas/serverless.yml`: la cola, la Lambda, SNS y el API Gateway
- `pruebas/e2e.sh`: la prueba de punta a punta

## Decisiones que tomamos

**¿Por qué una cola y no llamar directo a la Lambda?** Para que el login no dependa de las alertas. auth-service deja el mensaje y responde de inmediato. Si la Lambda o el correo fallan, el login sigue funcionando y los mensajes quedan esperando en la cola.

**Si SQS falla, el login no se cae.** El mensaje se manda en un hilo aparte (usamos los hilos virtuales de Java 25). Si SQS no responde, queda el error en el log y el usuario recibe su respuesta igual. Lo probamos con una cola falsa que fallaba, y los login siguieron respondiendo normal.

**El mensaje sale después de guardar en la base.** Usamos `@TransactionalEventListener`. Así, si el registro falla y se deshace, no sale un aviso de "usuario nuevo" que en verdad no existe.

**auth-service cuenta los fallidos, no la Lambda.** Al principio pensamos en que la Lambda llevara la cuenta, pero para eso necesitaba guardar datos en algún lado (por ejemplo DynamoDB). Como auth-service ya tiene el historial en MySQL, manda el número listo en el mensaje. Así la Lambda no guarda nada, y tampoco importa si los mensajes llegan desordenados.

**Reintentos y DLQ.** Si a la Lambda le falla un mensaje (por ejemplo, SNS no responde), le dice a SQS cuál falló y solo ese se reintenta. Si falla 3 veces, se va a la DLQ, donde queda 14 días para revisarlo. Si llega algo que no es JSON, la Lambda lo descarta, porque reintentarlo no lo va a arreglar.

**La Lambda es Java sin Spring.** En clases vimos `aws-serverless-java-container` para subir Spring a Lambda, pero no funciona con Spring Boot 4. Como la función es chica, la hicimos en Java puro: el jar pesa unos 11 MB y arranca más rápido.

**No hay llaves de AWS en el código.** La EC2 usa el rol `LabInstanceProfile`, y de ahí auth-service saca credenciales temporales para escribir en SQS. La Lambda usa `LabRole`, como en el ejemplo del profe. En AWS Academy no se pueden crear roles, así que usamos los del laboratorio.

**API Gateway.** Lo pusimos por tres cosas:
- La API queda con HTTPS sin tener que configurar certificados.
- Limita a 20 peticiones por segundo, lo que ayuda contra alguien que intente adivinar claves muy rápido.
- Solo publica las rutas que usa un cliente. `/api/v1/internal/**` es solo para los otros microservicios, así que no la pusimos (desde el API Gateway responde 404).

## Pruebas

- `SqsPublicadorEventosTest`: el JSON que llega a SQS, y que un error de SQS no rompa nada
- `AuthServiceImplTest`: el registro y el login fallido publican evento; el login correcto no
- `EventosAuthIntegracionTest`: registro + 3 fallidos dan 1, 2 y 3. Después de un login correcto, la cuenta vuelve a 1
- `AlertasHandlerTest` (Lambda): avisa con 3 y 6 fallidos, no con 1, 2 o 4. Si SNS falla, solo ese mensaje vuelve a la cola
- `pruebas/e2e.sh`: corre contra el API Gateway de verdad. Revisa HTTPS, health, que la ruta interna no esté, registro, login, token, que un vecino no pueda listar usuarios, 3 logins fallidos, y en los logs de CloudWatch que la Lambda haya mandado la alerta

Para correr la prueba e2e a mano (la URL sale en el pipeline, en el job de Serverless, paso "Mostrar las salidas"):

```bash
bash pruebas/e2e.sh https://<id>.execute-api.us-east-1.amazonaws.com
```

Cada vez que corre deja un usuario `e2e-...@rutalimpia.cl` en la base.

## Dónde mirarlo en AWS

- **SQS > rl-auth-eventos-prod > Monitoring:** los mensajes que llegan y se procesan. La DLQ debería estar vacía.
- **Lambda > rl-auth-eventos-prod-alertas > Monitor > View CloudWatch logs:** las líneas `ALERTA enviada usuarioId=...`
- **SNS > Topics > rl-auth-alertas-prod:** el correo suscrito tiene que estar *Confirmed*.
- **API Gateway > rl-auth-api-prod > Routes:** las 4 rutas.

## Lo que nos faltó

- La IP que queda en `registros_acceso` es una interna del Swarm, no la del cliente. Para tener la real habría que leer el header `X-Forwarded-For` que manda el API Gateway.
- El puerto 8081 de la EC2 sigue abierto. Lo ideal sería que solo el API Gateway pudiera entrar, pero para eso se necesita un VPC Link con un balanceador, y lo dejamos para después.
- SQS puede entregar un mensaje dos veces, así que en algún caso podría llegar un correo repetido.
