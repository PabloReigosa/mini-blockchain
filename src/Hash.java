import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {

    /** Devuelve el SHA-256 de un texto, en hexadecimal (64 caracteres). */
    public static String sha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // Main TEMPORAL solo para probar. Lo borraremos después.
    public static void main(String[] args) {
        String entrada = "fba1219648b62d6448463ae363dc8e59f7241e4d801f4f4f150cc3f7fdf086ac"
                + "|Ana entrega 5 fichas a Luis|996";
        String esperado = "0005c654f763b4c9f4629eebcd7b6d2d1390100ec68eded0e93a5fa263a39094";
        String obtenido = sha256(entrada);
        System.out.println(obtenido);
        System.out.println(obtenido.equals(esperado) ? "OK" : "FALLO");
    }
}