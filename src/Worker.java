public class Worker {

    public static void main(String[] args) {
        // Argumentos: hashAnterior datos dificultad idWorker totalWorkers
        if (args.length != 5) {
            System.err.println("Uso: Worker <hashAnterior> <datos> <dificultad> <idWorker> <totalWorkers>");
            System.exit(1);
        }

        String hashAnterior = args[0];
        String datos = args[1];
        int dificultad;
        int id;
        int total;
        try {
            dificultad = Integer.parseInt(args[2]);
            id = Integer.parseInt(args[3]);
            total = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Dificultad, id y total deben ser numeros enteros");
            System.exit(1);
            return;
        }

        String prefijo = "0".repeat(dificultad);

        // Reparto: el worker i prueba i, i+total, i+2*total, ...
        for (long nonce = id; ; nonce += total) {
            String hash = Hash.sha256(hashAnterior + "|" + datos + "|" + nonce);
            if (hash.startsWith(prefijo)) {
                System.out.println(nonce + " " + hash);
                System.out.flush();
                return;
            }
        }
    }
}