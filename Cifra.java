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

import java.security.KeyStore;
import java.security.cert.Certificate;


public class Cifra {

    public static void main(String[] args) throws Exception {

    //gerar uma chave aleatoria para utilizar com o AES
    KeyGenerator kg = KeyGenerator.getInstance("AES");
    kg.init(128);
    SecretKey key = kg.generateKey();
    //obter certificado da keystore
    FileInputStream kfile = new FileInputStream("myKeys");
    KeyStore kstore = KeyStore.getInstance("PKCS12");
    kstore.load(kfile, "changeit".toCharArray());
    Certificate cert = kstore.getCertificate("rsakey");

    Cipher c = Cipher.getInstance("AES");
    c.init(Cipher.ENCRYPT_MODE, key);

    FileInputStream fis;
    FileOutputStream fos;
    CipherOutputStream cos;
    
    fis = new FileInputStream("a.txt");
    fos = new FileOutputStream("a.cif");

    cos = new CipherOutputStream(fos, c);
    byte[] b = new byte[16];  
    int i = fis.read(b);
    while (i != -1) {
        cos.write(b, 0, i);
        i = fis.read(b);
    }
    cos.close();

    byte[] keyEncoded = key.getEncoded();
    FileOutputStream kos = new FileOutputStream("a.key");
    kos.write(keyEncoded);
    kos.close();

    }
}