import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.*;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.crypto.*;
import javax.crypto.spec.*;

public class mySaude {

    private static final int BUFFER_SIZE = 4096;

    // -------------------------------------------------------------------------
    // Entry point & argument parsing
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String serverAddr = null;
        String username   = null;
        String password   = null;
        String target     = null;
        String command    = null;
        List<String> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-s":
                    if (i + 1 < args.length) serverAddr = args[++i];
                    else { System.err.println("Error: -s requires an argument."); System.exit(1); }
                    break;
                case "-u":
                    if (i + 1 < args.length) username = args[++i];
                    else { System.err.println("Error: -u requires an argument."); System.exit(1); }
                    break;
                case "-p":
                    if (i + 1 < args.length) password = args[++i];
                    else { System.err.println("Error: -p requires an argument."); System.exit(1); }
                    break;
                case "-t":
                    if (i + 1 < args.length) target = args[++i];
                    else { System.err.println("Error: -t requires an argument."); System.exit(1); }
                    break;
                case "-e":
                case "-r":
                case "-c":
                case "-d":
                case "-ce":
                case "-rd":
                case "-a":
                case "-v":
                case "-ae":
                case "-rv":
                case "-ace":
                case "-rdv":
                    command = args[i];
                    // Collect following filenames until the next flag or end of args
                    while (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        files.add(args[++i]);
                    }
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        // --- Common validations ---
        if (username == null) {
            System.err.println("Error: -u <username> is required.");
            System.exit(1);
        }
        if (command == null) {
            System.err.println("Error: no command specified.");
            printUsage();
            System.exit(1);
        }
        if (files.isEmpty()) {
            System.err.println("Error: no files specified.");
            System.exit(1);
        }

        // --- Parse server address (only needed for network commands) ---
        String serverHost = null;
        int    serverPort = -1;
        boolean needsServer = isNetworkCommand(command);

        if (serverAddr != null) {
            int colonIdx = serverAddr.lastIndexOf(':');
            if (colonIdx < 0) {
                System.err.println("Error: invalid server address format. Expected <ip>:<port>, got: " + serverAddr);
                System.exit(1);
            }
            serverHost = serverAddr.substring(0, colonIdx);
            try {
                serverPort = Integer.parseInt(serverAddr.substring(colonIdx + 1));
            } catch (NumberFormatException e) {
                System.err.println("Error: invalid port in server address: " + serverAddr);
                System.exit(1);
            }
        }

        if (needsServer) {
            if (serverAddr == null) {
                System.err.println("Error: -s <address:port> is required for command " + command);
                System.exit(1);
            }
            if (password == null) {
                System.err.println("Error: -p <password> is required for server authentication in command " + command);
                System.exit(1);
            }
        }

        // --- Dispatch ---
        switch (command) {
            case "-e":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -e"); System.exit(1); }
                sendFiles(serverHost, serverPort, username, password, files, target);
                break;

            case "-r":
                receiveFiles(serverHost, serverPort, username, password, files);
                break;

            case "-c":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -c"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -c"); System.exit(1); }
                encryptFiles(username, password, files, target, serverHost, serverPort);
                break;

            case "-d":
                if (password == null) { System.err.println("Error: -p <password> is required for -d"); System.exit(1); }
                decryptFiles(username, password, files);
                break;

            case "-ce":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -ce"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -ce"); System.exit(1); }
                encryptAndSendFiles(serverHost, serverPort, username, password, files, target);
                break;

            case "-rd":
                if (password == null) { System.err.println("Error: -p <password> is required for -rd"); System.exit(1); }
                receiveAndDecryptFiles(serverHost, serverPort, username, password, files);
                break;

            case "-a":
                if (password == null) { System.err.println("Error: -p <password> is required for -a"); System.exit(1); }
                signFiles(username, password, files);
                break;

            case "-v":
                if (password == null) { System.err.println("Error: -p <password> is required for -v"); System.exit(1); }
                if (target == null) { System.err.println("Error: -t <quem_assinou> is required for -v"); System.exit(1); }
                verifyFiles(username, password, files, target, serverHost, serverPort);
                break;

            case "-ae":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -ae"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -ae"); System.exit(1); }
                signAndSendFiles(serverHost, serverPort, username, password, files, target);
                break;

            case "-rv":
                if (target == null) { System.err.println("Error: -t <quem_assinou> is required for -rv"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -rv"); System.exit(1); }
                receiveAndVerifyFiles(serverHost, serverPort, username, password, files, target);
                break;

            case "-ace":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -ace"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -ace"); System.exit(1); }
                signEncryptAndSendFiles(serverHost, serverPort, username, password, files, target);
                break;

            case "-rdv":
                if (target == null) { System.err.println("Error: -t <quem_assinou> is required for -rdv"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -rdv"); System.exit(1); }
                receiveDecryptAndVerifyFiles(serverHost, serverPort, username, password, files, target);
                break;

            default:
                System.err.println("Unknown command: " + command);
                System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // -e : send files to the server (into the target user's directory)
    //
    // Protocol (must match mySaudeServer.handleSend):
    //   C→S  "SEND"
    //   C→S  senderUsername   (writeObject)
    //   C→S  password         (writeObject)
    //   C→S  targetUsername   (writeObject)
    //   C→S  fileCount        (writeInt)
    //   for each file:
    //     C→S  filename       (writeObject)
    //     C→S  fileSize       (writeLong)
    //     C→S  <bytes>        (write)
    //     S→C  "OK:..."  or  "ERROR:..."  (readObject)
    // -------------------------------------------------------------------------

    static void sendFiles(String host, int port, String username, String password,
                          List<String> files, String target) {

        // Client-side check: filter files that don't exist locally before connecting
        List<File> validFiles = new ArrayList<>();
        for (String filePath : files) {
            File f = new File(filePath);
            if (!f.exists() || !f.isFile()) {
                System.err.println("Error: file not found locally: " + filePath);
            } else {
                validFiles.add(f);
            }
        }

        if (validFiles.isEmpty()) {
            System.out.println("No valid files to send.");
            return;
        }

        try (SSLSocket socket = createTlsSocket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 1. Command + sender + password + target + number of files
            out.writeObject("SEND");
            out.writeObject(username);
            out.writeObject(password);
            out.writeObject(target);
            out.writeInt(validFiles.size());
            out.flush();

            // 2. Send each file and read the server's per-file response
            for (File file : validFiles) {
                String filename = file.getName();
                long fileSize   = file.length();

                out.writeObject(filename);
                out.writeLong(fileSize);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                // Server replies "OK: ..." or "ERROR: ..." for every file
                String response = (String) in.readObject();
                if (response.startsWith("OK")) {
                    System.out.println("Sent: " + filename);
                } else {
                    System.err.println("Error for '" + filename + "': " + response);
                }
            }

        } catch (ConnectException e) {
            System.err.println("Error: cannot connect to server at " + host + ":" + port);
        } catch (SSLHandshakeException e) {
            System.err.println("TLS handshake failed (server certificate is not trusted/valid): " + e.getMessage());
        } catch (SSLException e) {
            System.err.println("TLS error while communicating with server: " + e.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // -r : receive files from the server (from the caller's own directory)
    //
    // Protocol (must match mySaudeServer.handleReceive):
    //   C→S  "RECEIVE"
    //   C→S  username         (writeObject)
    //   C→S  password         (writeObject)
    //   C→S  fileCount        (writeInt)
    //   for each file:
    //     C→S  filename       (writeObject)
    //     S→C  "OK" or "ERROR"  (readObject)
    //     S→C  filename         (readObject)   — always echoed back
    //     if OK:
    //       S→C  fileSize     (readLong)
    //       S→C  <bytes>      (read)
    //     if ERROR:
    //       S→C  errorMessage (readObject)
    // -------------------------------------------------------------------------

    static void receiveFiles(String host, int port, String username, String password, List<String> files) {

        // Client-side check: skip files that already exist locally before connecting
        List<String> toReceive = new ArrayList<>();
        for (String filePath : files) {
            String filename = new File(filePath).getName();
            if (new File(filename).exists()) {
                System.err.println("Error: file '" + filename + "' already exists locally. Skipping.");
            } else {
                toReceive.add(filename);
            }
        }

        if (toReceive.isEmpty()) {
            System.out.println("No files to receive.");
            return;
        }

        try (SSLSocket socket = createTlsSocket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 1. Command + username + password + number of files
            out.writeObject("RECEIVE");
            out.writeObject(username);
            out.writeObject(password);
            out.writeInt(toReceive.size());
            out.flush();

            // 2. Request each file and read the server's response
            for (String filename : toReceive) {
                out.writeObject(filename);
                out.flush();

                String status       = (String) in.readObject();
                String echoFilename = (String) in.readObject(); // server always echoes the filename

                if ("OK".equals(status)) {
                    long fileSize = in.readLong();
                    File localFile = new File(echoFilename);

                    try (FileOutputStream fos = new FileOutputStream(localFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        long remaining = fileSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = in.read(buffer, 0, toRead);
                            if (read == -1) throw new EOFException("Unexpected EOF receiving " + echoFilename);
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }
                    }
                    System.out.println("Received: " + echoFilename);

                } else {
                    String errorMsg = (String) in.readObject();
                    System.err.println("Error for '" + echoFilename + "': " + errorMsg);
                }
            }

        } catch (ConnectException e) {
            System.err.println("Error: cannot connect to server at " + host + ":" + port);
        } catch (SSLHandshakeException e) {
            System.err.println("TLS handshake failed (server certificate is not trusted/valid): " + e.getMessage());
        } catch (SSLException e) {
            System.err.println("TLS error while communicating with server: " + e.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // -c : hybrid encryption  (AES-128/CBC + RSA-2048)
    //
    // For each file:
    //   1. Generate random AES-128 key and IV
    //   2. Encrypt file  → <file>.cifrado   (IV prepended, 16 bytes)
    //   3. Encrypt AES key with recipient's RSA public key (from keystore)
    //                    → <file>.chave.<target>
    //
    // Keystore used: keystore.<username>  (PKCS12, alias = username)
    // Recipient's certificate must be imported with alias = target username
    // -------------------------------------------------------------------------

    static void encryptFiles(String username, String password,
                             List<String> files, String target,
                             String serverHost, int serverPort) {
        KeyStore ks = loadKeyStore(username, password);
        if (ks == null) return;

        // Get recipient's public key from local keystore or fetch it from the server.
        Certificate cert = getCertificateOrFetch(ks, username, password, target, serverHost, serverPort);
        if (cert == null) return;
        PublicKey recipientPublicKey = cert.getPublicKey();

        for (String filePath : files) {
            File inputFile = new File(filePath);

            if (!inputFile.exists() || !inputFile.isFile()) {
                System.err.println("Error: file not found: " + filePath);
                continue;
            }

            String filename     = inputFile.getName();
            File encryptedFile  = new File(filename + ".cifrado");
            File keyFile        = new File(filename + ".chave." + target);

            if (encryptedFile.exists()) {
                System.err.println("Error: '" + encryptedFile.getName() + "' already exists. Skipping.");
                continue;
            }

            try {
                // 1. Generate AES-128 key and random IV
                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(128);
                SecretKey aesKey = kg.generateKey();

                byte[] iv = new byte[16];
                new SecureRandom().nextBytes(iv);
                IvParameterSpec ivSpec = new IvParameterSpec(iv);

                // 2. Encrypt file: IV (16 bytes) || ciphertext  → <file>.cifrado
                Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);

                try (FileInputStream  fis = new FileInputStream(inputFile);
                     FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                    fos.write(iv);                           // prepend IV
                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = fis.read(buf)) != -1) {
                        byte[] block = aesCipher.update(buf, 0, n);
                        if (block != null) fos.write(block);
                    }
                    byte[] last = aesCipher.doFinal();
                    if (last != null) fos.write(last);
                }

                // 3. Encrypt AES key with RSA public key of recipient → <file>.chave.<target>
                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
                byte[] encryptedKey = rsaCipher.doFinal(aesKey.getEncoded());

                try (FileOutputStream kos = new FileOutputStream(keyFile)) {
                    kos.write(encryptedKey);
                }

                System.out.println("Encrypted: " + encryptedFile.getName()
                                 + " + " + keyFile.getName());

            } catch (Exception e) {
                System.err.println("Error encrypting '" + filename + "': " + e.getMessage());
                encryptedFile.delete();
                keyFile.delete();
            }
        }
    }

    // -------------------------------------------------------------------------
    // -d : hybrid decryption
    //
    // For each <file>.cifrado:
    //   1. Find <file>.chave.<username>
    //   2. Decrypt the AES key with the user's RSA private key (from keystore)
    //   3. Read IV from first 16 bytes of .cifrado
    //   4. Decrypt ciphertext with AES/CBC/PKCS5Padding  → <file>
    // -------------------------------------------------------------------------

    static void decryptFiles(String username, String password, List<String> files) {
        KeyStore ks = loadKeyStore(username, password);
        if (ks == null) return;

        // Get user's RSA private key
        PrivateKey privateKey;
        try {
            privateKey = (PrivateKey) ks.getKey(username, password.toCharArray());
        } catch (Exception e) {
            System.err.println("Error retrieving private key from keystore: " + e.getMessage());
            return;
        }
        if (privateKey == null) {
            System.err.println("Error: private key for '" + username + "' not found in keystore.");
            return;
        }

        for (String filePath : files) {
            if (!filePath.endsWith(".cifrado")) {
                System.err.println("Error: '" + filePath + "' does not end in .cifrado. Skipping.");
                continue;
            }

            File encryptedFile = new File(filePath);
            if (!encryptedFile.exists() || !encryptedFile.isFile()) {
                System.err.println("Error: file not found: " + filePath);
                continue;
            }

            String encName   = encryptedFile.getName();
            String baseName  = encName.substring(0, encName.length() - ".cifrado".length());
            File keyFile     = new File(baseName + ".chave." + username);
            File outputFile  = new File(baseName);

            if (!keyFile.exists()) {
                System.err.println("Error: key file not found: " + keyFile.getName()
                                 + ". Cannot decrypt " + encName + ".");
                continue;
            }
            if (outputFile.exists()) {
                System.err.println("Error: output file '" + baseName + "' already exists. Skipping.");
                continue;
            }

            try {
                // 1. Read and decrypt the AES key with RSA private key
                byte[] encryptedKey = Files.readAllBytes(keyFile.toPath());
                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
                byte[] aesKeyBytes = rsaCipher.doFinal(encryptedKey);
                SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");

                // 2. Read IV (first 16 bytes) and decrypt the rest
                try (FileInputStream  fis = new FileInputStream(encryptedFile);
                     FileOutputStream fos = new FileOutputStream(outputFile)) {

                    byte[] iv = new byte[16];
                    if (fis.read(iv) != 16) {
                        throw new IOException("Invalid .cifrado file: could not read IV.");
                    }
                    IvParameterSpec ivSpec = new IvParameterSpec(iv);

                    Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);

                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = fis.read(buf)) != -1) {
                        byte[] block = aesCipher.update(buf, 0, n);
                        if (block != null) fos.write(block);
                    }
                    byte[] last = aesCipher.doFinal();
                    if (last != null) fos.write(last);
                }

                System.out.println("Decrypted: " + baseName);

            } catch (Exception e) {
                System.err.println("Error decrypting '" + encName + "': " + e.getMessage());
                outputFile.delete();
            }
        }
    }


    static void encryptAndSendFiles(String host, int port,
                                String username, String password,
                                List<String> files, String target) {

        // 1) Cifrar localmente
        encryptFiles(username, password, files, target, host, port);

        // 2) Preparar lista dos ficheiros gerados
        List<String> generatedFiles = new ArrayList<>();

        for (String filePath : files) {
            String baseName = new File(filePath).getName();

            File encryptedFile = new File(baseName + ".cifrado");
            File keyFile = new File(baseName + ".chave." + target);

            if (encryptedFile.exists() && encryptedFile.isFile() &&
                keyFile.exists() && keyFile.isFile()) {
                generatedFiles.add(encryptedFile.getPath());
                generatedFiles.add(keyFile.getPath());
            } else {
                System.err.println("Error: encrypted output missing for '" + baseName + "'. Skipping send.");
            }
        }

        if (generatedFiles.isEmpty()) {
            System.out.println("No encrypted files to send.");
            return;
        }

        // 3) Enviar para o servidor
        sendFiles(host, port, username, password, generatedFiles, target);
    }


    static void receiveAndDecryptFiles(String host, int port,
                                   String username, String password,
                                   List<String> files) {

        List<String> filesToReceive = new ArrayList<>();

        for (String name : files) {
            filesToReceive.add(name + ".cifrado");
            filesToReceive.add(name + ".chave." + username);
        }

        // 1) Receber do servidor os ficheiros necessários
        receiveFiles(host, port, username, password, filesToReceive);

        // 2) Decifrar os .cifrado recebidos
        List<String> encryptedFiles = new ArrayList<>();
        for (String name : files) {
            encryptedFiles.add(name + ".cifrado");
        }

        decryptFiles(username, password, encryptedFiles);
    }


    static void signFiles(String username, String password, List<String> files) {
        try {
            KeyStore ks = loadKeyStore(username, password);
            PrivateKey privateKey = (PrivateKey) ks.getKey(username, password.toCharArray());

            if (privateKey == null) {
                System.err.println("Error: private key for user '" + username + "' not found in keystore.");
                return;
            }

            for (String filePath : files) {
                File inputFile = new File(filePath);

                if (!inputFile.exists() || !inputFile.isFile()) {
                    System.err.println("Error: file '" + filePath + "' does not exist. Skipping.");
                    continue;
                }

                String signatureFileName = inputFile.getName() + ".assinatura." + username;
                File signatureFile = new File(signatureFileName);

                if (signatureFile.exists()) {
                    System.err.println("Error: signature file '" + signatureFileName + "' already exists. Skipping.");
                    continue;
                }

                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(privateKey);

                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = bis.read(buffer)) != -1) {
                        sig.update(buffer, 0, n);
                    }
                }

                byte[] signatureBytes = sig.sign();

                try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                    fos.write(signatureBytes);
                }

                System.out.println("Signed: " + inputFile.getName() + " -> " + signatureFileName);
            }

        } catch (Exception e) {
            System.err.println("Error signing files: " + e.getMessage());
        }
    }


    static void verifyFiles(String username, String password, List<String> files, String signer,
                            String serverHost, int serverPort) {
        KeyStore ks = loadKeyStore(username, password);
        if (ks == null) return;

        Certificate cert = getCertificateOrFetch(ks, username, password, signer, serverHost, serverPort);
        if (cert == null) return;
        PublicKey publicKey = cert.getPublicKey();

        for (String filePath : files) {
            File inputFile = new File(filePath);

            if (!inputFile.exists() || !inputFile.isFile()) {
                System.err.println("Error: file '" + filePath + "' does not exist. Skipping.");
                continue;
            }

            String signatureFileName = inputFile.getName() + ".assinatura." + signer;
            File signatureFile = new File(signatureFileName);

            if (!signatureFile.exists()) {
                System.err.println("Error: signature file '" + signatureFileName + "' not found.");
                continue;
            }

            try {
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(publicKey);

                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = bis.read(buffer)) != -1) {
                        sig.update(buffer, 0, n);
                    }
                }

                byte[] signatureBytes = Files.readAllBytes(signatureFile.toPath());
                boolean ok = sig.verify(signatureBytes);

                if (ok) {
                    System.out.println("Signature valid: " + inputFile.getName() + " (signed by " + signer + ")");
                } else {
                    System.out.println("Signature INVALID: " + inputFile.getName() + " (signed by " + signer + ")");
                }
            } catch (Exception e) {
                System.err.println("Error verifying '" + filePath + "': " + e.getMessage());
            }
        }
    }


    static void signAndSendFiles(String host, int port,
                             String username, String password,
                             List<String> files, String target) {

        signFiles(username, password, files);

        List<String> generatedFiles = new ArrayList<>();

        for (String filePath : files) {
            File original = new File(filePath);
            String baseName = original.getName();

            File signedCopy = new File(baseName + ".assinado");
            File signatureFile = new File(baseName + ".assinatura." + username);

            try {
                Files.copy(original.toPath(), signedCopy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Error creating signed copy for '" + baseName + "': " + e.getMessage());
                continue;
            }

            if (signedCopy.exists() && signatureFile.exists()) {
                generatedFiles.add(signedCopy.getPath());
                generatedFiles.add(signatureFile.getPath());
            } else {
                System.err.println("Error: missing signed output for '" + baseName + "'.");
            }
        }

        if (generatedFiles.isEmpty()) {
            System.out.println("No signed files to send.");
            return;
        }

        sendFiles(host, port, username, password, generatedFiles, target);
    }


    static void receiveAndVerifyFiles(String host, int port,
                                  String username, String password,
                                  List<String> files, String signer) {

        List<String> filesToReceive = new ArrayList<>();

        for (String name : files) {
            filesToReceive.add(name + ".assinado");
            filesToReceive.add(name + ".assinatura." + signer);
        }

        receiveFiles(host, port, username, password, filesToReceive);

        for (String name : files) {
            File signedFile = new File(name + ".assinado");
            File restoredFile = new File(name);

            if (signedFile.exists()) {
                try {
                    Files.copy(signedFile.toPath(), restoredFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Error restoring signed file '" + name + "': " + e.getMessage());
                    continue;
                }
            }
        }

        verifyFiles(username, password, files, signer, host, port);
    }



    static void signEncryptAndSendFiles(String host, int port,
                                    String username, String password,
                                    List<String> files, String target) {

        try {
            KeyStore ks = loadKeyStore(username, password);
            PrivateKey privateKey = (PrivateKey) ks.getKey(username, password.toCharArray());

            if (privateKey == null) {
                System.err.println("Error: private key for user '" + username + "' not found.");
                return;
            }

            Certificate cert = getCertificateOrFetch(ks, username, password, target, host, port);
            if (cert == null) return;

            PublicKey recipientPublicKey = cert.getPublicKey();

            List<String> generatedFiles = new ArrayList<>();

            for (String filePath : files) {
                File inputFile = new File(filePath);

                if (!inputFile.exists() || !inputFile.isFile()) {
                    System.err.println("Error: file '" + filePath + "' does not exist. Skipping.");
                    continue;
                }

                String baseName = inputFile.getName();

                File envelopeFile = new File(baseName + ".envelope");
                File signatureFile = new File(baseName + ".assinatura." + username);
                File keyFile = new File(baseName + ".chave." + target);

                if (envelopeFile.exists() || signatureFile.exists() || keyFile.exists()) {
                    System.err.println("Error: output file(s) already exist for '" + baseName + "'. Skipping.");
                    continue;
                }

                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initSign(privateKey);

                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = bis.read(buffer)) != -1) {
                        sig.update(buffer, 0, n);
                    }
                }

                byte[] signatureBytes = sig.sign();

                try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                    fos.write(signatureBytes);
                }

                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(128);
                SecretKey aesKey = kg.generateKey();

                byte[] iv = new byte[16];
                new SecureRandom().nextBytes(iv);
                IvParameterSpec ivSpec = new IvParameterSpec(iv);

                Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec);

                try (FileInputStream fis = new FileInputStream(inputFile);
                     FileOutputStream fos = new FileOutputStream(envelopeFile)) {
                    fos.write(iv); // prepend IV
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = fis.read(buffer)) != -1) {
                        byte[] block = aesCipher.update(buffer, 0, n);
                        if (block != null) fos.write(block);
                    }
                    byte[] last = aesCipher.doFinal();
                    if (last != null) fos.write(last);
                }

                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
                byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

                try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                    fos.write(encryptedAesKey);
                }

                generatedFiles.add(envelopeFile.getPath());
                generatedFiles.add(signatureFile.getPath());
                generatedFiles.add(keyFile.getPath());

                System.out.println("Signed+Encrypted: " + baseName + " -> "
                        + envelopeFile.getName() + " + "
                        + signatureFile.getName() + " + "
                        + keyFile.getName());
            }

            if (generatedFiles.isEmpty()) {
                System.out.println("No envelope files to send.");
                return;
            }

            sendFiles(host, port, username, password, generatedFiles, target);

        } catch (Exception e) {
            System.err.println("Error in -ace: " + e.getMessage());
        }
    }


    static void receiveDecryptAndVerifyFiles(String host, int port,
                                         String username, String password,
                                         List<String> files, String signer) {

        List<String> filesToReceive = new ArrayList<>();

        for (String name : files) {
            filesToReceive.add(name + ".envelope");
            filesToReceive.add(name + ".assinatura." + signer);
            filesToReceive.add(name + ".chave." + username);
        }

        // 1) Receber os ficheiros do servidor
        receiveFiles(host, port, username, password, filesToReceive);

        // 2) Decifrar os envelopes recebidos
        try {
            KeyStore ks = loadKeyStore(username, password);
            PrivateKey privateKey = (PrivateKey) ks.getKey(username, password.toCharArray());

            if (privateKey == null) {
                System.err.println("Error: private key for user '" + username + "' not found.");
                return;
            }

            for (String name : files) {
                File envelopeFile = new File(name + ".envelope");
                File keyFile = new File(name + ".chave." + username);
                File outputFile = new File(name);

                if (!envelopeFile.exists() || !keyFile.exists()) {
                    System.err.println("Error: missing envelope/key for '" + name + "'. Skipping.");
                    continue;
                }

                if (outputFile.exists()) {
                    System.err.println("Error: output file '" + name + "' already exists. Skipping decrypt.");
                    continue;
                }

                byte[] encryptedAesKey = Files.readAllBytes(keyFile.toPath());

                Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
                byte[] aesKeyBytes = rsaCipher.doFinal(encryptedAesKey);

                SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

                try (FileInputStream fis = new FileInputStream(envelopeFile);
                     FileOutputStream fos = new FileOutputStream(outputFile)) {

                    byte[] iv = new byte[16];
                    if (fis.read(iv) != 16) {
                        throw new IOException("Envelope file invalid: could not read IV.");
                    }
                    IvParameterSpec ivSpec = new IvParameterSpec(iv);

                    Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                    aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec);

                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = fis.read(buffer)) != -1) {
                        byte[] block = aesCipher.update(buffer, 0, n);
                        if (block != null) fos.write(block);
                    }
                    byte[] last = aesCipher.doFinal();
                    if (last != null) fos.write(last);
                }

                System.out.println("Decrypted: " + name);
            }

        } catch (Exception e) {
            System.err.println("Error decrypting in -rdv: " + e.getMessage());
            return;
        }

        // 3) Verificar assinatura do ficheiro decifrado
        verifyFiles(username, password, files, signer, host, port);
    }



    // -------------------------------------------------------------------------
    // Keystore loader — shared by -c, -d, -a, -v
    //
    // File: keystore.<username>  (PKCS12 format)
    // Alias for private key and own certificate: <username>
    // Aliases for other users' certificates: <their_username>
    // -------------------------------------------------------------------------

    static KeyStore loadKeyStore(String username, String password) {
        String ksPath = "keystore." + username;
        File ksFile = new File(ksPath);
        if (!ksFile.exists()) {
            System.err.println("Error: keystore not found: " + ksPath);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(fis, password.toCharArray());
            return ks;
        } catch (Exception e) {
            System.err.println("Error loading keystore '" + ksPath + "': " + e.getMessage());
            return null;
        }
    }


    static void saveKeyStore(KeyStore ks, String username, String password) throws Exception {
        String ksPath = "keystore." + username;
        try (FileOutputStream fos = new FileOutputStream(ksPath)) {
            ks.store(fos, password.toCharArray());
        }
    }

    static Certificate getCertificateOrFetch(KeyStore ks, String username, String password,
                                             String requestedUser, String serverHost, int serverPort) {
        try {
            Certificate cert = ks.getCertificate(requestedUser);
            if (cert != null) return cert;
        } catch (KeyStoreException e) {
            System.err.println("Error accessing keystore: " + e.getMessage());
            return null;
        }

        if (serverHost == null || serverPort <= 0) {
            System.err.println("Error: certificate for '" + requestedUser + "' not found in keystore." +
                    " Use -s <server:port> so mySaude can request it from the server, or import it manually.");
            return null;
        }

        Certificate cert = requestCertificateFromServer(serverHost, serverPort, username, password, requestedUser);
        if (cert == null) return null;

        try {
            ks.setCertificateEntry(requestedUser, cert);
            saveKeyStore(ks, username, password);
            System.out.println("Certificate for '" + requestedUser + "' imported into keystore." + username);
            return cert;
        } catch (Exception e) {
            System.err.println("Error saving certificate for '" + requestedUser + "' in keystore." + username + ": " + e.getMessage());
            return null;
        }
    }

    static Certificate requestCertificateFromServer(String host, int port, String username,
                                                    String password, String requestedUser) {
        try (SSLSocket socket = createTlsSocket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("GET_CERT");
            out.writeObject(username);
            out.writeObject(password);
            out.writeObject(requestedUser);
            out.flush();

            String status = (String) in.readObject();
            if ("OK".equals(status)) {
                byte[] certBytes = (byte[]) in.readObject();
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (ByteArrayInputStream bis = new ByteArrayInputStream(certBytes)) {
                    return cf.generateCertificate(bis);
                }
            }

            String errorMsg = (String) in.readObject();
            System.err.println("Error requesting certificate for '" + requestedUser + "': " + errorMsg);
            return null;

        } catch (ConnectException e) {
            System.err.println("Error: cannot connect to server at " + host + ":" + port);
        } catch (SSLHandshakeException e) {
            System.err.println("TLS handshake failed while requesting certificate: " + e.getMessage());
        } catch (SSLException e) {
            System.err.println("TLS error while requesting certificate: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error requesting certificate from server: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isNetworkCommand(String cmd) {
        switch (cmd) {
            case "-e": case "-r":
            case "-ce": case "-rd":
            case "-ae": case "-rv":
            case "-ace": case "-rdv":
                return true;
            default:
                return false;
        }
    }

    private static SSLSocket createTlsSocket(String host, int port) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();
        return socket;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -e <files...> -t <dest>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -r <files...>");
        System.out.println("  mySaude -u <user> -p <pass> -c <files...> -t <dest>");
        System.out.println("  mySaude -u <user> -p <pass> -d <files...>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -ce <files...> -t <dest>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -rd <files...>");
        System.out.println("  mySaude -u <user> -p <pass> -a <files...>");
        System.out.println("  mySaude -u <user> -p <pass> -v <files...> -t <quem_assinou>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -ae <files...> -t <dest>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -rv <files...> -t <quem_assinou>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -ace <files...> -t <dest>");
        System.out.println("  mySaude -s <addr:port> -u <user> -p <pass> -rdv <files...> -t <quem_assinou>");
    }
}
