import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class criarUser {

    private static final String USERS_FILE = "users";
    private static final String MAC_FILE = "mySaude.mac";
    private static final String STORAGE_DIR = "server_storage";
    private static final String USERS_KEYSTORE = "keystore.users";
    private static final char[] USERS_KEYSTORE_PASSWORD = "changeit".toCharArray(); // simplificação operacional

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 6 || !"-f".equals(args[3])) {
            System.err.println("Uso: criarUser <username> <funcao> <password> -f <ficheiro com o certificado do utilizador>");
            throw new IllegalArgumentException("Argumentos inválidos.");
        }

        String username = args[0];
        String role = args[1];
        String password = args[2];
        String certPath = args[4];

        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username vazio.");
        }
        if (!isValidRole(role)) {
            throw new IllegalArgumentException("Função inválida. Use 'medico' ou 'utente'.");
        }

        ensureUsersFileExists();

        String macPassword = readMacPasswordFromConsole();
        if (macPassword == null || macPassword.isEmpty()) {
            throw new IllegalArgumentException("Password MAC vazia.");
        }
        SecretKey macKey = new SecretKeySpec(macPassword.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        // Verificar integridade antes de qualquer acesso/alteração ao ficheiro users
        verifyUsersMacBeforeAccess(macKey);

        if (findUserLine(username) != null) {
            throw new IllegalArgumentException("Username já existe: " + username);
        }

        Certificate cert = loadX509Certificate(certPath);

        // 1) Adicionar user ao ficheiro users (salt + hash)
        byte[] salt = generateSalt();
        byte[] hash = hashPasswordWithSalt(salt, password);
        String line = formatUserLine(username, role, salt, hash);
        appendLineToUsers(line);

        // 2) Criar diretoria server_storage/<username>/
        File userDir = new File(STORAGE_DIR, username);
        if (!userDir.exists() && !userDir.mkdirs()) {
            throw new IOException("Não foi possível criar a diretoria do utilizador: " + userDir.getPath());
        }

        // 3) Adicionar certificado à keystore.users com alias = username
        upsertCertificateInUsersKeyStore(username, cert);

        // 4) Atualizar MAC após alteração do ficheiro users
        updateUsersMacAfterChange(macKey);

        System.out.println("Utilizador criado com sucesso: " + username + " (" + role + ")");
    }

    private static boolean isValidRole(String role) {
        return "medico".equals(role) || "utente".equals(role);
    }

    private static void ensureUsersFileExists() throws IOException {
        File usersFile = new File(USERS_FILE);
        if (!usersFile.exists()) {
            if (!usersFile.createNewFile()) {
                throw new IOException("Não foi possível criar o ficheiro '" + USERS_FILE + "'.");
            }
        }
    }

    private static String readMacPasswordFromConsole() throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword("Introduza a password MAC do servidor: ");
            return chars == null ? null : new String(chars);
        }
        System.out.print("Introduza a password MAC do servidor: ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return br.readLine();
    }

    private static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] hashPasswordWithSalt(byte[] salt, String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update(password.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }

    private static String formatUserLine(String username, String role, byte[] salt, byte[] hash) {
        return username + ":" + role + ":" +
                Base64.getEncoder().encodeToString(salt) + ":" +
                Base64.getEncoder().encodeToString(hash);
    }

    private static void appendLineToUsers(String line) throws IOException {
        Files.writeString(Path.of(USERS_FILE), line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private static String findUserLine(String username) throws IOException {
        File usersFile = new File(USERS_FILE);
        if (!usersFile.exists()) return null;

        try (BufferedReader br = Files.newBufferedReader(usersFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(":");
                if (parts.length >= 1 && username.equals(parts[0])) {
                    return line; // termina assim que encontra
                }
            }
        }
        return null;
    }

    private static byte[] computeUsersMac(SecretKey key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] usersBytes = Files.readAllBytes(Path.of(USERS_FILE));
        return mac.doFinal(usersBytes);
    }

    private static byte[] readMacFromFile() throws IOException {
        String content = Files.readString(Path.of(MAC_FILE), StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) throw new IOException("Ficheiro MAC vazio.");
        return Base64.getDecoder().decode(content);
    }

    private static void writeMacToFile(byte[] macBytes) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(macBytes);
        Files.writeString(Path.of(MAC_FILE), encoded, StandardCharsets.UTF_8);
    }

    private static void verifyUsersMacBeforeAccess(SecretKey key) throws Exception {
        File macFile = new File(MAC_FILE);
        if (!macFile.exists()) {
            // Caso inicial: permite se users ainda está vazio (criarUser vai atualizar no fim)
            if (new File(USERS_FILE).length() == 0) {
                return;
            }
            throw new SecurityException("Ficheiro MAC '" + MAC_FILE + "' não existe.");
        }

        byte[] expected = readMacFromFile();
        byte[] current = computeUsersMac(key);
        if (!MessageDigest.isEqual(expected, current)) {
            throw new SecurityException("MAC inválido para o ficheiro '" + USERS_FILE + "'.");
        }
    }

    private static void updateUsersMacAfterChange(SecretKey key) throws Exception {
        byte[] updated = computeUsersMac(key);
        writeMacToFile(updated);
    }

    private static Certificate loadX509Certificate(String certPath) throws Exception {
        File certFile = new File(certPath);
        if (!certFile.exists() || !certFile.isFile()) {
            throw new IllegalArgumentException("Certificado não existe: " + certPath);
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(certFile)) {
            return cf.generateCertificate(fis);
        }
    }

    private static void upsertCertificateInUsersKeyStore(String alias, Certificate cert) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        File ksFile = new File(USERS_KEYSTORE);

        if (ksFile.exists()) {
            try (FileInputStream fis = new FileInputStream(ksFile)) {
                ks.load(fis, USERS_KEYSTORE_PASSWORD);
            }
        } else {
            ks.load(null, USERS_KEYSTORE_PASSWORD);
        }

        // Simplificação: alias = username; se já existir, substitui.
        ks.setCertificateEntry(alias, cert);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(ksFile)) {
            ks.store(fos, USERS_KEYSTORE_PASSWORD);
        }
    }
}

