# 6. Multi-tenant por discriminador, no por esquema ni por base de datos

**Estado:** aceptada · **Fecha:** agosto 2026

## Contexto

Varias empresas usan la misma instancia, y ninguna puede ver los datos de otra.
Hay tres formas habituales de conseguirlo:

1. **Una base de datos por empresa.** Aislamiento máximo; operación pesada
   (migrar N bases, N conexiones) y caro en un plan gratuito.
2. **Un esquema por empresa.** Punto intermedio, pero sigue habiendo que migrar
   N esquemas y enrutar la conexión en cada petición.
3. **Discriminador**: una columna `empresa_id` en las tablas, y filtrar por ella.

El punto de partida ya usaba discriminador, pero **mal**: el aislamiento era
artesanal y solo un método comprobaba realmente que el recurso perteneciera a la
empresa de quien lo pedía. Otro devolvía todos los usuarios de la empresa
—gestores incluidos— cuando debía devolver solo el equipo.

## Decisión

Mantener el **discriminador**, pero hacerlo sistemático:

- `empresa_id` **denormalizado** en `registros` y `peticiones_ausencia`. Antes el
  tenant solo se alcanzaba navegando `registro → usuario → empresa`, lo que
  obligaba a un `JOIN` para algo tan básico como filtrar. Ahora se filtra
  directo y se aprovechan los índices `(empresa_id, ...)`.
- Comprobación explícita en toda operación sobre un recurso ajeno, con una
  excepción propia (`TenantAccessException` → 403) distinta de la de "no tienes
  el rol": aquí el rol **sí** lo tienes, lo que falla es que el recurso no es de
  tu empresa.
- **Tests de aislamiento** que crean dos empresas y comprueban que la una no ve
  ni toca nada de la otra, tanto en servicios como en repositorios.

Se evaluó y **descartó** `@FilterDef`/`@Filter` de Hibernate para filtrar
automáticamente: hay que habilitarlo por sesión, no se aplica a `findById` y
falla en silencio si se olvida. Un filtro que a veces no filtra es peor que no
tenerlo, porque invita a confiarse. El filtrado explícito más los tests cubren
el requisito de forma verificable.

## Consecuencias

**A favor**

- Una sola base de datos: una migración, un pool de conexiones, encaja en el
  plan gratuito.
- Consultas más simples y con mejor uso de índices tras denormalizar.
- El aislamiento está **verificado por tests**, no supuesto.

**En contra**

- El aislamiento depende de que **cada consulta filtre**. Es el riesgo real de
  este enfoque, y por eso los tests cruzados no son opcionales.
- `empresa_id` duplicado en dos tablas: hay que mantenerlo coherente al crear
  las filas. Se asigna en un único punto, al construir la entidad.
- No sirve si algún día un cliente exige aislamiento físico de sus datos. Para
  el alcance de este proyecto no aplica.
