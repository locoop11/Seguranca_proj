/***************************************************************************
*   Seguranca Informatica
*   mySaudeServer - versão base para TP1
*   Suporta operações SEND e RECEIVE sem autenticação
***************************************************************************/

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class mySaudeServer {

    private static final String STORAGE_DIR = "server_storage";
    private static final int BUFFER_SIZE = 4096;

    private ServerSocket serverSocket = null;

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

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Servidor à escuta no porto " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ServerThread(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
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

                if (destinationFile.exists()) {
                    discardBytes(in, fileSize);
                    out.writeObject("ERROR: o ficheiro '" + filename + "' já existe no servidor");
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
