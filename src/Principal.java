import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Principal {

    // Dificultad por defecto. Se puede cambiar aqui o pasando un numero como argumento.
    static int DIFICULTAD = 3;
    static final int NUM_WORKERS = 4;

    /** Un bloque ya aceptado en la cadena. */
    record Bloque(int numero, String datos, String hashAnterior, long nonce, String hash,
                  int workerGanador, long pidGanador, String hashRecibido) { }

    /** Lo que cada worker comunica al principal (linea null = acabo sin resultado). */
    record Resultado(int id, long pid, String linea) { }

    public static void main(String[] args) {
        // 1. Dificultad
        if (args.length > 0) {
            try {
                DIFICULTAD = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Error: la dificultad debe ser un numero entero.");
                return;
            }
        }
        if (DIFICULTAD < 1 || DIFICULTAD > 64) {
            System.err.println("Error: la dificultad debe estar entre 1 y 64.");
            return;
        }

        // 2. Seed y fichero
        Scanner sc = new Scanner(System.in);
        System.out.print("Semilla inicial (seed): ");
        String seed = sc.hasNextLine() ? sc.nextLine().strip() : "";
        if (seed.isEmpty()) {
            System.err.println("Error: la semilla no puede estar vacia.");
            return;
        }
        System.out.print("Ruta del fichero de datos: ");
        String ruta = sc.hasNextLine() ? sc.nextLine().strip() : "";

        List<String> lineas;
        try {
            lineas = Files.readAllLines(Path.of(ruta), StandardCharsets.UTF_8).stream()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty())
                    .toList();
        } catch (IOException | RuntimeException e) {
            System.err.println("Error: no se puede leer el fichero '" + ruta + "' (" + e.getMessage() + ").");
            return;
        }
        if (lineas.isEmpty()) {
            System.out.println("El fichero no contiene datos: no hay bloques que procesar.");
            return;
        }

        // 3. Construccion de la cadena
        List<Bloque> cadena = new ArrayList<>();
        String hashAnterior = Hash.sha256(seed);
        System.out.println("\nHash inicial (seed): " + hashAnterior);
        System.out.println("Dificultad: " + DIFICULTAD + " | Bloques a minar: " + lineas.size() + "\n");

        try {
            for (int i = 0; i < lineas.size(); i++) {
                Bloque b = minar(i + 1, lineas.get(i), hashAnterior);
                cadena.add(b);
                imprimir(b);
                hashAnterior = b.hash();
                if (i + 1 < lineas.size()) {
                    System.out.println("\nPreparando el bloque " + (i + 2) + "...\n");
                }
            }
        } catch (IOException | InterruptedException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage() + ". Se detiene el minado.");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        imprimirCadena(cadena, Hash.sha256(seed));
    }

    /** Mina un bloque con 4 workers. Al salir (bien o mal) todos los workers estan finalizados. */
    static Bloque minar(int numero, String datos, String hashAnterior)
            throws IOException, InterruptedException {
        List<Process> procesos = new ArrayList<>();
        BlockingQueue<Resultado> cola = new LinkedBlockingQueue<>();
        try {
            for (int id = 0; id < NUM_WORKERS; id++) {
                procesos.add(lanzarWorker(id, hashAnterior, datos, cola));
            }
            int recibidos = 0;
            while (recibidos < NUM_WORKERS) {
                Resultado r = cola.take();   // el primero que llegue, sea de quien sea
                recibidos++;
                Bloque b = validar(r, numero, datos, hashAnterior);
                if (b != null) {
                    return b;                // primera solucion valida: nos quedamos con ella
                }
            }
            throw new IllegalStateException("Ningun worker aporto una solucion valida para el bloque " + numero);
        } finally {
            finalizarWorkers(procesos);
        }
    }

    /** Lanza un worker como proceso independiente y un hilo que lee su salida. */
    static Process lanzarWorker(int id, String hashAnterior, String datos,
                                BlockingQueue<Resultado> cola) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(java, "-cp", classpath, "Worker",
                hashAnterior, datos, String.valueOf(DIFICULTAD),
                String.valueOf(id), String.valueOf(NUM_WORKERS));
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();

        Thread lector = new Thread(() -> {
            String linea = null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                linea = br.readLine();       // null si el worker acaba sin imprimir nada
            } catch (IOException e) {
                // el proceso fue terminado: linea queda en null
            }
            cola.add(new Resultado(id, p.pid(), linea));
        });
        lector.setDaemon(true);
        lector.start();
        return p;
    }

    /** Comprueba la propuesta de un worker. Devuelve el bloque si es valida, o null si se rechaza. */
    static Bloque validar(Resultado r, int numero, String datos, String hashAnterior) {
        if (r.linea() == null) {
            System.out.println("  [Worker " + r.id() + "] termino sin aportar resultado.");
            return null;
        }
        String[] partes = r.linea().strip().split(" ");
        long nonce;
        try {
            if (partes.length != 2) {
                throw new NumberFormatException();
            }
            nonce = Long.parseLong(partes[0]);
        } catch (NumberFormatException e) {
            System.out.println("  [Worker " + r.id() + "] RECHAZADO: formato incorrecto -> " + r.linea());
            return null;
        }
        String recalculado = Hash.sha256(hashAnterior + "|" + datos + "|" + nonce);
        boolean coincide = recalculado.equals(partes[1]);
        boolean prefijoOk = recalculado.startsWith("0".repeat(DIFICULTAD));
        if (nonce < 0 || !coincide || !prefijoOk) {
            System.out.println("  [Worker " + r.id() + "] RECHAZADO: hash o dificultad incorrectos.");
            return null;
        }
        return new Bloque(numero, datos, hashAnterior, nonce, recalculado,
                r.id(), r.pid(), partes[1]);
    }

    /** Termina todos los workers y comprueba que ninguno sigue vivo. */
    static void finalizarWorkers(List<Process> procesos) throws InterruptedException {
        for (Process p : procesos) {
            p.destroy();
        }
        for (Process p : procesos) {
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor();
            }
        }
        for (Process p : procesos) {
            if (p.isAlive()) {
                throw new IllegalStateException("El worker con PID " + p.pid() + " sigue activo");
            }
        }
    }

    static void imprimir(Bloque b) {
        System.out.println("BLOQUE " + b.numero() + " VALIDADO\n");
        System.out.println("Datos: " + b.datos());
        System.out.println("Hash anterior: " + b.hashAnterior());
        System.out.println("Dificultad: " + DIFICULTAD);
        System.out.println("Worker ganador: " + b.workerGanador() + " - PID: " + b.pidGanador());
        System.out.println("Nonce encontrado: " + b.nonce() + "\n");
        System.out.println("Hash recibido:\n" + b.hashRecibido() + "\n");
        System.out.println("Hash recalculado por el principal:\n" + b.hash() + "\n");
        System.out.println("Validacion: hashes coincidentes y prefijo de " + DIFICULTAD + " ceros correcto.");
        System.out.println("Bloque incorporado a la cadena.");
        System.out.println("Workers de esta busqueda: todos finalizados.");
    }

    static void imprimirCadena(List<Bloque> cadena, String hashInicial) {
        System.out.println("\n===== CADENA FINAL =====");
        String esperado = hashInicial;
        boolean ok = true;
        for (Bloque b : cadena) {
            boolean enlazado = b.hashAnterior().equals(esperado);
            ok &= enlazado;
            System.out.println("Bloque " + b.numero() + " | nonce " + b.nonce() + " | enlace "
                    + (enlazado ? "OK" : "ROTO") + " | " + b.hash());
            esperado = b.hash();
        }
        System.out.println(ok ? "Cadena integra: " + cadena.size() + " bloques enlazados."
                : "ATENCION: la cadena tiene enlaces rotos.");
    }
}