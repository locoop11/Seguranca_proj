/***************************************************************************
*   Seguranca Informatica
*
*
***************************************************************************/
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class mySaudeServer {

    private static final String EXPECTED_USER = "user";
    private static final String EXPECTED_PASS = "pass";

    // Minimal change: promote ServerSocket to a field so it can be closed from shutdown hook
    private ServerSocket sSoc = null;

    public static void main(String[] args) {
        int port = 23456; // default port
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println("Invalid port number. Using default 23456.");
                    port = 23456;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument. Using default 23456.");
                port = 23456;
            }
        } else {
            System.out.println("No port argument provided. Using default port 23456.");
        }

        mySaudeServer server = new mySaudeServer();

        // Minimal shutdown hook to close the ServerSocket on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook: closing ServerSocket...");
            try {
                if (server.sSoc != null && !server.sSoc.isClosed()) {
                    server.sSoc.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing ServerSocket in shutdown hook: " + e.getMessage());
            }
        }));

        server.startServer(port);
    }

    public void startServer(int port) {
        try {
            sSoc = new ServerSocket(port);
            System.out.println("Server listening on port " + port);
        } catch (IOException e) {
            System.err.println("Failed to open ServerSocket on port " + port + ": " + e.getMessage());
            System.exit(-1);
        }

        try {
            while (true) {
                try {
                    Socket inSoc = sSoc.accept();
                    ServerThread newServerThread = new ServerThread(inSoc);
                    newServerThread.start();
                } catch (IOException e) {
                    // If sSoc was closed (e.g., by shutdown hook), accept() will throw.
                    if (sSoc == null || sSoc.isClosed()) {
                        System.out.println("ServerSocket closed, stopping accept loop.");
                        break;
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            // ensure ServerSocket is closed if startServer exits for any reason
            if (sSoc != null && !sSoc.isClosed()) {
                try {
                    sSoc.close();
                } catch (IOException ignored) {}
            }
        }
    }

    // Threads used for communication with clients
    class ServerThread extends Thread {

        private Socket socket = null;
        private static final int BUFFER_SIZE = 4096;

        ServerThread(Socket inSoc) {
            socket = inSoc;
            System.out.println("Cliente novo: " + socket.getRemoteSocketAddress() + ", nova thread criada");
        }

        public void run() {
            ObjectOutputStream outStream = null;
            ObjectInputStream inStream = null;

            try {
                // Create ObjectOutputStream first, then ObjectInputStream (match client)
                outStream = new ObjectOutputStream(socket.getOutputStream());
                outStream.flush();
                inStream = new ObjectInputStream(socket.getInputStream());

                String user = null;
                String passwd = null;

				try {
					user = (String) inStream.readObject();
					passwd = (String) inStream.readObject();
					System.out.println("Cliente autenticado como user: " + user);
				} catch (EOFException eof) {
					// orderly close by client before sending credentials
					System.out.println("Cliente fechou conecção antes de autenticar");
					return;
				} catch (java.net.SocketException se) {
					// connection reset / abort by client
					System.out.println("Cliente fechou conecção antes de autenticar");
					return;
				} catch (IOException ioe) {
					// other I/O errors while reading credentials
					System.err.println("I/O error while reading credentials: " + ioe.getMessage());
					ioe.printStackTrace();
					return;
				} catch (ClassNotFoundException e1) {
					e1.printStackTrace();
					return;
				}

                // Authentication check: only accept EXPECTED_USER / EXPECTED_PASS
                boolean authOk = EXPECTED_USER.equals(user) && EXPECTED_PASS.equals(passwd);
                outStream.writeObject(Boolean.valueOf(authOk));
                outStream.flush();

                if (!authOk) {
                    System.out.println("Authentication failed for user: " + user);
                    return; // close connection
                }

                // --- After authentication: receive a file ---
                // Protocol expected from client:
                // 1) client sends filename as String (Object)
                // 2) client sends file size as Long (Object)
                // 3) client sends raw file bytes (write(byte[],off,len) repeatedly)
                // 4) client sends an additional String (Object) after bytes

                try {
                    Object objFilename = inStream.readObject();
                    if (!(objFilename instanceof String)) {
                        outStream.writeObject("ERROR: expected filename String");
                        outStream.flush();
                        return;
                    }

                    String filename = (String) objFilename;
                    System.out.println("Receiving file: " + filename);

                    Object objSize = inStream.readObject();
                    if (!(objSize instanceof Long)) {
                        outStream.writeObject("ERROR: expected file size Long");
                        outStream.flush();
                        return;
                    }

                    long fileSize = (Long) objSize;
                    System.out.println("Declared file size: " + fileSize + " bytes");

                    File outFile = new File("server_received_" + filename);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        long remaining = fileSize;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = inStream.read(buffer, 0, toRead);
                            if (read == -1) {
                                throw new EOFException("Unexpected end of stream while receiving file");
                            }
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }
                        fos.flush();
                    }

                    System.out.println("File saved as: " + outFile.getAbsolutePath());

                    Object objExtra = inStream.readObject();
                    if (objExtra instanceof String) {
                        String extra = (String) objExtra;
                        System.out.println("Received additional string from client: " + extra);
                        outStream.writeObject("ACK: received file " + filename + " (" + fileSize + " bytes) and message: " + extra);
                        outStream.flush();
                    } else {
                        outStream.writeObject("ERROR: expected extra String after file");
                        outStream.flush();
                    }

                    System.out.println("Transfer complete, closing connection with client: " + socket.getRemoteSocketAddress());
                } catch (EOFException eof) {
                    System.out.println("Cliente fechou conecção durante transferência de ficheiro (EOF)");
                    return;
                } catch (java.net.SocketException sockEx) {
                    System.out.println("Socket reset/closed by client after auth (need no file upload): " + sockEx.getMessage());
                    return;
                } catch (ClassNotFoundException cnf) {
                    System.err.println("ClassNotFound while parsing post-auth object: " + cnf.getMessage());
                    cnf.printStackTrace();
                    return;
                }


            } catch (IOException e) {
                System.err.println("I/O error in ServerThread: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try { if (inStream != null) inStream.close(); } catch (IOException ignored) {}
                try { if (outStream != null) outStream.close(); } catch (IOException ignored) {}
                try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}