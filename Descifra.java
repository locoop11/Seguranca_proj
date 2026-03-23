import java.io.FileInputStream;  
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.Certificate;


public class Descifra {

    public static void main(String[] args) throws Exception {

    byte[] keyEncoded2 = Files.readAllBytes(Paths.get("a.key"));
    SecretKeySpec keySpec2 = new SecretKeySpec(keyEncoded2, "AES");
    Cipher c = Cipher.getInstance("AES");
    c.init(Cipher.DECRYPT_MODE, keySpec2);
    try (FileInputStream fis = new FileInputStream("a.cif");
        CipherInputStream cis = new CipherInputStream(fis, c);
        FileOutputStream fos = new FileOutputStream("a.dec")) {
        byte[] buffer = new byte[4096];
        int n;
        while ((n = cis.read(buffer)) != -1) {
            fos.write(buffer, 0, n); 
        }
        }
    }
}