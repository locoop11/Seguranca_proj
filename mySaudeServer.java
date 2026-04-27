/***************************************************************************
*   Seguranca Informatica
*   mySaudeServer - versão base para TP1
*   Suporta operações SEND e RECEIVE sem autenticação
***************************************************************************/

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.net.Socket;

public class mySaudeServer {

    private static final String STORAGE_DIR = "server_storage";
    private static final String USERS_FILE = "users";
    private static final String MAC_FILE = "mySaude.mac";
    private static final int BUFFER_SIZE = 4096;
    private static SecretKey usersMacKey;


    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java mySaudeServer <porto>");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                System.out.println("Porto inválido. Use um valor entre 1 e 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Porto inválido: " + args[0]);
            return;
        }

        mySaudeServer server = new mySaudeServer();
        server.startServer(port);
    }

    public void startServer(int port) {
        createBaseStorage();
        ensureUsersFileExists();
        if (!initializeUsersMacProtection()) {
            return;
        }
        SSLServerSocket sslServerSocket = null;

        try {
            String ksPath = "keystore.server";
            char[] ksPassword = "changeit".toCharArray();

            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(ksPath)) {
                ks.load(fis, ksPassword);
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());                         
            kmf.init(ks, ksPassword);

            SSLContext sslContext = SSLContext.getInstance("TLS");                  //context
            sslContext.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();            // fábrica de sockets SSL com a keystore carregada
            sslServerSocket = (SSLServerSocket) ssf.createServerSocket(port);           //usar a Fabrica para criar o ojeto socket SSL
            System.out.println("Servidor à escuta no porto " + port);

            while (true) {
                Socket clientSocket = sslServerSocket.accept();
                new ServerThread(clientSocket).start();
            }
        } catch (Exception e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        } finally {
            if (sslServerSocket != null && !sslServerSocket.isClosed()) {
                try {
                    sslServerSocket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void createBaseStorage() {
        File baseDir = new File(STORAGE_DIR);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            System.err.println("Aviso: não foi possível criar a pasta base " + STORAGE_DIR);
        }
    }

    private void ensureUsersFileExists() {
        File usersFile = new File(USERS_FILE);
        if (usersFile.exists()) {
            return;
        }
        try {
            if (!usersFile.createNewFile()) {
                System.err.println("Aviso: não foi possível criar o ficheiro '" + USERS_FILE + "'.");
            }
        } catch (IOException e) {
            System.err.println("Aviso: erro ao criar o ficheiro '" + USERS_FILE + "': " + e.getMessage());
        }
    }

    private boolean initializeUsersMacProtection() {
        String macPassword = readMacPasswordFromConsole();
        if (macPassword == null || macPassword.isEmpty()) {
            System.err.println("Erro: password MAC vazia.");
            return false;
        }
        usersMacKey = new SecretKeySpec(macPassword.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        try {
            File macFile = new File(MAC_FILE);
            byte[] currentMac = computeUsersMac(usersMacKey);

            if (!macFile.exists()) {
                if (new File(USERS_FILE).length() == 0) {
                    writeMacToFile(currentMac);
                    System.out.println("MAC inicial criado em '" + MAC_FILE + "'.");
                    return true;
                }
                System.err.println("Erro: ficheiro MAC '" + MAC_FILE + "' não existe.");
                return false;
            }

            byte[] storedMac = readMacFromFile();
            if (!MessageDigest.isEqual(storedMac, currentMac)) {
                System.err.println("Erro: integridade do ficheiro '" + USERS_FILE + "' comprometida (MAC inválido).");
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("Erro ao validar MAC de utilizadores: " + e.getMessage());
            return false;
        }
    }

    private String readMacPasswordFromConsole() {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword("Introduza a password MAC do servidor: ");
            return chars == null ? null : new String(chars);
        }
        System.out.print("Introduza a password MAC do servidor: ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    static class UserRecord {
        final String username;
        final String role;
        final byte[] salt;
        final byte[] passwordHash;

        UserRecord(String username, String role, byte[] salt, byte[] passwordHash) {
            this.username = username;
            this.role = role;
            this.salt = salt;
            this.passwordHash = passwordHash;
        }
    }

    static boolean isValidRole(String role) {
        return "medico".equals(role) || "utente".equals(role);
    }

    static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    static byte[] hashPasswordWithSalt(byte[] salt, String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        digest.update(password.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }

    static String encodeBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] decodeBase64(String encoded) {
        return Base64.getDecoder().decode(encoded);
    }

    static String formatUserLine(String username, String role, byte[] salt, byte[] hash) {
        return username + ":" + role + ":" + encodeBase64(salt) + ":" + encodeBase64(hash);
    }

    static UserRecord parseUserLine(String line) {
        String[] parts = line.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Linha de utilizador inválida no ficheiro users.");
        }
        String username = parts[0];
        String role = parts[1];
        if (!isValidRole(role)) {
            throw new IllegalArgumentException("Função inválida para o utilizador '" + username + "'.");
        }
        byte[] salt = decodeBase64(parts[2]);
        byte[] hash = decodeBase64(parts[3]);
        return new UserRecord(username, role, salt, hash);
    }

    static UserRecord findUserByUsername(String username) throws Exception {
        verifyUsersMacBeforeAccess();
        File usersFile = new File(USERS_FILE);
        if (!usersFile.exists()) {
            return null;
        }

        try (FileReader fr = new FileReader(usersFile, StandardCharsets.UTF_8);
             java.io.BufferedReader br = new java.io.BufferedReader(fr)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                UserRecord user = parseUserLine(line);
                if (username.equals(user.username)) {
                    return user; // terminate immediately once the user is found
                }
            }
        }
        return null;
    }

    static boolean authenticateUser(String username, String password) throws Exception {
        UserRecord user = findUserByUsername(username);
        if (user == null) {
            return false;
        }
        byte[] computedHash = hashPasswordWithSalt(user.salt, password);
        return MessageDigest.isEqual(user.passwordHash, computedHash);
    }

    static boolean addUser(String username, String role, String password) throws Exception {
        verifyUsersMacBeforeAccess();
        if (!isValidRole(role)) {
            throw new IllegalArgumentException("Função inválida. Use 'medico' ou 'utente'.");
        }
        if (findUserByUsername(username) != null) {
            return false;
        }

        byte[] salt = generateSalt();
        byte[] hash = hashPasswordWithSalt(salt, password);
        String userLine = formatUserLine(username, role, salt, hash);

        try (FileWriter writer = new FileWriter(USERS_FILE, StandardCharsets.UTF_8, true)) {
            writer.write(userLine);
            writer.write(System.lineSeparator());
        }
        updateUsersMacAfterChange();
        return true;
    }

    static void verifyUsersMacBeforeAccess() throws Exception {
        if (usersMacKey == null) {
            throw new IllegalStateException("Chave MAC não inicializada.");
        }

        File macFile = new File(MAC_FILE);
        if (!macFile.exists()) {
            throw new SecurityException("Ficheiro MAC '" + MAC_FILE + "' não existe.");
        }

        byte[] expectedMac = readMacFromFile();
        byte[] currentMac = computeUsersMac(usersMacKey);
        if (!MessageDigest.isEqual(expectedMac, currentMac)) {
            throw new SecurityException("MAC inválido para o ficheiro '" + USERS_FILE + "'.");
        }
    }

    static void updateUsersMacAfterChange() throws Exception {
        if (usersMacKey == null) {
            throw new IllegalStateException("Chave MAC não inicializada.");
        }
        byte[] updatedMac = computeUsersMac(usersMacKey);
        writeMacToFile(updatedMac);
    }

    static byte[] computeUsersMac(SecretKey key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] usersBytes = Files.readAllBytes(Path.of(USERS_FILE));
        return mac.doFinal(usersBytes);
    }

    static void writeMacToFile(byte[] macBytes) throws IOException {
        String encoded = Base64.getEncoder().encodeToString(macBytes);
        Files.writeString(Path.of(MAC_FILE), encoded, StandardCharsets.UTF_8);
    }

    static byte[] readMacFromFile() throws IOException {
        String content = Files.readString(Path.of(MAC_FILE), StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) {
            throw new IOException("Ficheiro MAC vazio.");
        }
        return Base64.getDecoder().decode(content);
    }

    class ServerThread extends Thread {
        private final Socket socket;

        ServerThread(Socket socket) {
            this.socket = socket;
            System.out.println("Novo cliente ligado: " + socket.getRemoteSocketAddress());
        }

        @Override
        public void run() {
            ObjectOutputStream out = null;
            ObjectInputStream in = null;

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                String command = (String) in.readObject();

                if ("SEND".equals(command)) {
                    handleSend(in, out);
                } else if ("RECEIVE".equals(command)) {
                    handleReceive(in, out);
                } else {
                    out.writeObject("ERROR: comando inválido");
                    out.flush();
                }

            } catch (EOFException e) {
                System.out.println("Ligação terminada pelo cliente: " + socket.getRemoteSocketAddress());
            } catch (Exception e) {
                System.err.println("Erro na thread do cliente " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ignored) {
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ignored) {
                }
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ignored) {
                }
            }
        }

        private void handleSend(ObjectInputStream in, ObjectOutputStream out) throws Exception {
            String senderUser = (String) in.readObject();
            String targetUser = (String) in.readObject();
            int fileCount = in.readInt();

            System.out.println("Pedido SEND de '" + senderUser + "' para '" + targetUser + "' com " + fileCount + " ficheiro(s)");

            File targetDir = new File(STORAGE_DIR, targetUser);
            if (!targetDir.exists() || !targetDir.isDirectory()) {
                for (int i = 0; i < fileCount; i++) {
                    String filename = (String) in.readObject();
                    long fileSize = in.readLong();
                    discardBytes(in, fileSize);
                    out.writeObject("ERROR: diretoria do utilizador destino não existe para o ficheiro " + filename);
                    out.flush();
                }
                return;
            }

            for (int i = 0; i < fileCount; i++) {
                String filename = (String) in.readObject();
                long fileSize = in.readLong();

                File destinationFile = new File(targetDir, filename);

                // Check exact match AND base-name variants (aa.pdf / aa.pdf.cifrado /
                // aa.pdf.assinado / aa.pdf.envelope cannot coexist for the same user)
                if (fileConflictExists(targetDir, filename)) {
                    discardBytes(in, fileSize);
                    out.writeObject("ERROR: o ficheiro '" + filename + "' (ou uma variante) já existe no servidor para o utilizador '" + targetUser + "'");
                    out.flush();
                    continue;
                }

                try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = fileSize;

                    while (remaining > 0) {
                        int bytesToRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = in.read(buffer, 0, bytesToRead);

                        if (bytesRead == -1) {
                            throw new EOFException("Fim inesperado da stream ao receber '" + filename + "'");
                        }

                        fos.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                out.writeObject("OK: ficheiro '" + filename + "' guardado para o utilizador '" + targetUser + "'");
                out.flush();
            }
        }

        private void handleReceive(ObjectInputStream in, ObjectOutputStream out) throws Exception {
            String user = (String) in.readObject();
            int fileCount = in.readInt();

            System.out.println("Pedido RECEIVE do utilizador '" + user + "' com " + fileCount + " ficheiro(s)");

            File userDir = new File(STORAGE_DIR, user);

            for (int i = 0; i < fileCount; i++) {
                String filename = (String) in.readObject();

                if (!userDir.exists() || !userDir.isDirectory()) {
                    out.writeObject("ERROR");
                    out.writeObject(filename);
                    out.writeObject("A diretoria do utilizador não existe no servidor");
                    out.flush();
                    continue;
                }

                File requestedFile = new File(userDir, filename);
                if (!requestedFile.exists() || !requestedFile.isFile()) {
                    out.writeObject("ERROR");
                    out.writeObject(filename);
                    out.writeObject("O ficheiro não existe no servidor");
                    out.flush();
                    continue;
                }

                out.writeObject("OK");
                out.writeObject(filename);
                out.writeLong(requestedFile.length());

                try (FileInputStream fis = new FileInputStream(requestedFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                out.flush();
            }
        }

        /** Returns true if filename (or any of its base-name variants) already exists. */
        private boolean fileConflictExists(File userDir, String filename) {
            String base = getBaseName(filename);
            for (String variant : new String[]{base, base + ".cifrado", base + ".assinado", base + ".envelope"}) {
                if (new File(userDir, variant).exists()) return true;
            }
            return false;
        }

        private String getBaseName(String filename) {
            if (filename.endsWith(".cifrado"))  return filename.substring(0, filename.length() - ".cifrado".length());
            if (filename.endsWith(".assinado")) return filename.substring(0, filename.length() - ".assinado".length());
            if (filename.endsWith(".envelope")) return filename.substring(0, filename.length() - ".envelope".length());
            return filename;
        }

        private void discardBytes(ObjectInputStream in, long bytesToSkip) throws IOException {
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = bytesToSkip;

            while (remaining > 0) {
                int bytesToRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = in.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) {
                    throw new EOFException("Fim inesperado da stream ao descartar bytes");
                }
                remaining -= bytesRead;
            }
        }
    }
}
