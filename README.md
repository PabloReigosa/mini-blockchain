# Mini block chain

Simulación de minado de una cadena de bloques en Java (DAM2). Un proceso principal construye la cadena a partir de un fichero de texto y, para cada bloque, lanza 4 procesos worker independientes que buscan en paralelo un nonce válido (prueba de trabajo).

## Requisitos

- JDK 17 o superior.

## Estructura

```
src/Hash.java        Utilidad SHA-256 (hexadecimal)
src/Worker.java      Proceso hijo: busca un nonce válido
src/Principal.java   Proceso principal: construye la cadena, lanza y valida
datos.txt            Fichero de datos de ejemplo
```

## Compilar

```
javac -d out src/*.java
```

## Ejecutar

```
java -cp out Principal
```

El programa pide por consola:

1. La **semilla inicial** (seed): cualquier texto no vacío.
2. La **ruta del fichero de datos**: por ejemplo `datos.txt`.

### Dificultad

La dificultad es el número de ceros que debe tener al principio cada hash. Por defecto es 3. Se puede cambiar de dos formas:

- Pasando un número como argumento: `java -cp out Principal 4`
- Modificando la variable `DIFICULTAD` de `Principal.java`.

Debe ser un entero entre 1 y 64. Con valores altos el minado tarda mucho más.

### Formato del fichero de datos

Un bloque por línea, en orden. Las líneas vacías se ignoran.

```
Ana entrega 5 fichas a Luis
Luis entrega 2 fichas a Marta
Marta entrega 1 ficha a Ana
```

## Cómo funciona

- **Hash del bloque:** `SHA-256(hash anterior + "|" + datos + "|" + nonce)`. El hash anterior del primer bloque es el SHA-256 de la semilla.
- **Reparto del trabajo:** el worker `i` (de 4) prueba los nonces `i, i+4, i+8, ...`, de modo que nunca coinciden dos workers en el mismo candidato.
- **Comunicación:** el principal pasa los datos a cada worker como argumentos del proceso (`ProcessBuilder`). El worker devuelve `nonce hash` por su salida estándar.
- **Recepción sin bloqueo:** un hilo lector por worker deja su resultado en una `BlockingQueue`; el principal atiende al primero que llegue.
- **Validación:** el principal recalcula el hash, comprueba que coincide con el recibido y que empieza por los ceros exigidos. Si no, rechaza la propuesta. Solo se acepta una solución por bloque.
- **Finalización:** al aceptar un bloque (o ante un error) se termina cada worker, se espera a su salida y se comprueba que ninguno sigue vivo antes de continuar.

## Ejemplo de salida

```
BLOQUE 1 VALIDADO

Datos: Ana entrega 5 fichas a Luis
Hash anterior: b221d9dbb083a7f33428d7c2a3c3198ae925614d70210e28716ccaa7cd4ddb79
Dificultad: 3
Worker ganador: 3 - PID: 22809
Nonce encontrado: 803
...
Validacion: hashes coincidentes y prefijo de 3 ceros correcto.
Bloque incorporado a la cadena.
Workers de esta busqueda: todos finalizados.
```
