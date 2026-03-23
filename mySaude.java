import java.io.*;
import java.net.*;

public class mySaude {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 4444;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        System.out.println("client: starting");
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {

            // Create ObjectOutputStream first, then ObjectInputStream (match server)
            ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());

            // --- Authentication (read from stdin using byte arrays) ---
            System.out.print("Enter username: ");
            String user = readLineFromStdin();

            System.out.print("Enter password: ");
            String passwd = readLineFromStdin();

            outStream.writeObject(user);
            outStream.writeObject(passwd);
            outStream.flush();

            // Read server response (expects a Boolean)
            Object resp = inStream.readObject();
            if (!(resp instanceof Boolean)) {
                System.err.println("Unexpected response from server: " + resp);
                closeStreams(outStream, inStream);
                return;
            }

            boolean authOk = (Boolean) resp;
            System.out.println("Server authentication result: " + authOk);

            if (!authOk) {
                System.out.println("Authentication failed. Closing connection.");
                closeStreams(outStream, inStream);
                return;
            }

            // --- After authentication: send a file (PDF recommended for tests) ---
            System.out.print("Enter path to PDF file to send: ");
            String path = readLineFromStdin();
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                System.err.println("File not found or not a regular file: " + path);
                closeStreams(outStream, inStream);
                return;
            }

            String filename = file.getName();
            long fileSize = file.length();

            // 1) send filename as String (Object)
            outStream.writeObject(filename);
            outStream.flush();

            // 2) send file size as Long (Object)
            outStream.writeObject(Long.valueOf(fileSize));
            outStream.flush();

            // 3) send raw bytes using write(byte[], off, len)
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                long totalSent = 0;
                while ((read = fis.read(buffer, 0, BUFFER_SIZE)) != -1) {
                    outStream.write(buffer, 0, read);
                    totalSent += read;
                }
                outStream.flush();
                System.out.println("Sent file bytes: " + totalSent + " bytes");
            }

            // 4) after file bytes, send an additional String
            String extraMessage = "FILE_SENT_OK";
            outStream.writeObject(extraMessage);
            outStream.flush();
            System.out.println("Sent additional string: " + extraMessage);

            // Optionally wait for server acknowledgement (example)
            try {
                Object ack = inStream.readObject();
                System.out.println("Server reply: " + ack);
            } catch (EOFException eof) {
                // server closed connection without reply
                System.out.println("Server closed connection (no ack).");
            }

            // Close streams and socket
            closeStreams(outStream, inStream);
            System.out.println("client: finished");

        } catch (UnknownHostException e) {
            System.err.println("Unknown host: " + SERVER_HOST);
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("I/O error communicating with server");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("Received unknown object from server");
            e.printStackTrace();
        }
    }

    private static String readLineFromStdin() throws IOException {
        byte[] buffer = new byte[256];
        int read = System.in.read(buffer, 0, buffer.length);
        if (read > 0) {
            // Remove trailing newline
            if (buffer[read - 1] == '\n') read--;
            if (read > 0 && buffer[read - 1] == '\r') read--;
            return new String(buffer, 0, read);
        }
        return "";
    }

    private static void closeStreams(ObjectOutputStream out, ObjectInputStream in) {
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
    }
}