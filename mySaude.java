import java.io.*;
import java.net.*;
import java.util.*;

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

        if (needsServer) {
            if (serverAddr == null) {
                System.err.println("Error: -s <address:port> is required for command " + command);
                System.exit(1);
            }
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

        // --- Dispatch ---
        switch (command) {
            case "-e":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -e"); System.exit(1); }
                sendFiles(serverHost, serverPort, username, files, target);
                break;

            case "-r":
                receiveFiles(serverHost, serverPort, username, files);
                break;

            case "-c":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -c"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -c"); System.exit(1); }
                System.err.println("Command -c not yet implemented.");
                break;

            case "-d":
                if (password == null) { System.err.println("Error: -p <password> is required for -d"); System.exit(1); }
                System.err.println("Command -d not yet implemented.");
                break;

            case "-ce":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -ce"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -ce"); System.exit(1); }
                System.err.println("Command -ce not yet implemented.");
                break;

            case "-rd":
                if (password == null) { System.err.println("Error: -p <password> is required for -rd"); System.exit(1); }
                System.err.println("Command -rd not yet implemented.");
                break;

            case "-a":
                if (password == null) { System.err.println("Error: -p <password> is required for -a"); System.exit(1); }
                System.err.println("Command -a not yet implemented.");
                break;

            case "-v":
                if (target == null) { System.err.println("Error: -t <quem_assinou> is required for -v"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -v"); System.exit(1); }
                System.err.println("Command -v not yet implemented.");
                break;

            case "-ae":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -ae"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -ae"); System.exit(1); }
                System.err.println("Command -ae not yet implemented.");
                break;

            case "-rv":
                if (target == null) { System.err.println("Error: -t <quem_assinou> is required for -rv"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -rv"); System.exit(1); }
                System.err.println("Command -rv not yet implemented.");
                break;

            case "-ace":
                if (target == null) { System.err.println("Error: -t <destinatario> is required for -ace"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -ace"); System.exit(1); }
                System.err.println("Command -ace not yet implemented.");
                break;

            case "-rdv":
                if (target == null) { System.err.println("Error: -t <quem_assinou> is required for -rdv"); System.exit(1); }
                if (password == null) { System.err.println("Error: -p <password> is required for -rdv"); System.exit(1); }
                System.err.println("Command -rdv not yet implemented.");
                break;

            default:
                System.err.println("Unknown command: " + command);
                System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // -e : send files to the server (into the target user's directory)
    // -------------------------------------------------------------------------

    static void sendFiles(String host, int port, String username,
                          List<String> files, String target) {
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 1. Announce command, sender and target
            out.writeObject("SEND");
            out.writeObject(username);
            out.writeObject(target);
            out.flush();

            // 2. Server checks whether the target directory exists
            String dirResp = (String) in.readObject();
            if ("ERR_NO_DIR".equals(dirResp)) {
                System.err.println("Error: directory for user '" + target + "' does not exist on server.");
                return;
            }
            if (!"OK".equals(dirResp)) {
                System.err.println("Unexpected response from server: " + dirResp);
                return;
            }

            // 3. Send each file
            for (String filePath : files) {
                File file = new File(filePath);
                String filename = file.getName();

                // Client-side check: file must exist locally
                if (!file.exists() || !file.isFile()) {
                    System.err.println("Error: file not found locally: " + filePath);
                    // Tell the server we are skipping this file
                    out.writeObject("SKIP");
                    out.flush();
                    continue;
                }

                // Propose the filename to the server
                out.writeObject(filename);
                out.flush();

                // Server checks for duplicates / conflicts
                String fileResp = (String) in.readObject();
                if ("ERR_FILE_EXISTS".equals(fileResp)) {
                    System.err.println("Error: file '" + filename + "' already exists on server for user '" + target + "'.");
                    continue;
                }
                if (!"OK".equals(fileResp)) {
                    System.err.println("Error from server for file '" + filename + "': " + fileResp);
                    continue;
                }

                // Send file size then raw bytes
                long fileSize = file.length();
                out.writeObject(fileSize);
                out.flush();

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }

                // Wait for acknowledgement
                String ack = (String) in.readObject();
                if ("ACK".equals(ack)) {
                    System.out.println("Sent: " + filename);
                } else {
                    System.err.println("Error sending file '" + filename + "': " + ack);
                }
            }

            // 4. Signal end of transfer
            out.writeObject("DONE");
            out.flush();

        } catch (ConnectException e) {
            System.err.println("Error: cannot connect to server at " + host + ":" + port);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // -r : receive files from the server (from the caller's own directory)
    // -------------------------------------------------------------------------

    static void receiveFiles(String host, int port, String username, List<String> files) {
        try (Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 1. Announce command and username
            out.writeObject("RECEIVE");
            out.writeObject(username);
            out.flush();

            // 2. Server checks whether the user directory exists
            String dirResp = (String) in.readObject();
            if ("ERR_NO_DIR".equals(dirResp)) {
                System.err.println("Error: directory for user '" + username + "' does not exist on server.");
                return;
            }
            if (!"OK".equals(dirResp)) {
                System.err.println("Unexpected response from server: " + dirResp);
                return;
            }

            // 3. Request each file
            for (String filePath : files) {
                String filename = new File(filePath).getName();

                // Check if the file already exists locally
                File localFile = new File(filename);
                if (localFile.exists()) {
                    System.err.println("Error: file '" + filename + "' already exists locally. Skipping.");
                    out.writeObject("SKIP");
                    out.flush();
                    continue;
                }

                out.writeObject(filename);
                out.flush();

                // Server responds with status
                String fileResp = (String) in.readObject();
                if ("ERR_FILE_NOT_FOUND".equals(fileResp)) {
                    System.err.println("Error: file '" + filename + "' does not exist on server for user '" + username + "'.");
                    continue;
                }
                if (!"OK".equals(fileResp)) {
                    System.err.println("Error from server for file '" + filename + "': " + fileResp);
                    continue;
                }

                // Receive file size then raw bytes
                long fileSize = (Long) in.readObject();

                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = fileSize;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int read = in.read(buffer, 0, toRead);
                        if (read == -1) throw new EOFException("Unexpected end of stream receiving " + filename);
                        fos.write(buffer, 0, read);
                        remaining -= read;
                    }
                }

                out.writeObject("ACK");
                out.flush();
                System.out.println("Received: " + filename);
            }

            // 4. Signal end
            out.writeObject("DONE");
            out.flush();

        } catch (ConnectException e) {
            System.err.println("Error: cannot connect to server at " + host + ":" + port);
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
        }
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

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mySaude -s <addr:port> -u <user> -e <files...> -t <dest>");
        System.out.println("  mySaude -s <addr:port> -u <user> -r <files...>");
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
