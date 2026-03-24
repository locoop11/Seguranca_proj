/***************************************************************************
 *  Seguranca Informatica
 *  criarUser — configura um utilizador no sistema mySaude
 *
 *  O que faz:
 *    1. Cria a diretoria do utilizador no servidor  (server_storage/<username>)
 *    2. Gera um par de chaves RSA-2048 numa keystore PKCS12 (keystore.<username>)
 *       com alias = username
 *    3. Exporta o certificado auto-assinado para <username>.cer  (formato PEM)
 *       para ser importado nas keystores de outros utilizadores
 *
 *  Uso:
 *    java criarUser <username> <password>
 *
 *  Exemplo:
 *    java criarUser maria passMaria
 *    java criarUser silva passSilva
 *
 *  Depois de criar dois utilizadores, partilhar os certificados:
 *    keytool -importcert -alias silva -keystore keystore.maria -file silva.cer
 *    keytool -importcert -alias maria -keystore keystore.silva -file maria.cer
 ***************************************************************************/

import java.io.*;

public class criarUser {

    private static final String STORAGE_DIR = "server_storage";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Uso: java criarUser <username> <password>");
            System.exit(1);
        }

        String username = args[0];
        String password = args[1];

        if (username.isEmpty() || password.isEmpty()) {
            System.err.println("Erro: username e password não podem ser vazios.");
            System.exit(1);
        }

        boolean ok = createUserDirectory(username)
                  && generateKeyStore(username, password)
                  && exportCertificate(username, password);

        if (ok) {
            System.out.println();
            System.out.println("Utilizador '" + username + "' criado com sucesso.");
            System.out.println("  Diretoria no servidor : " + STORAGE_DIR + "/" + username);
            System.out.println("  Keystore              : keystore." + username);
            System.out.println("  Certificado           : " + username + ".cer");
            System.out.println();
            System.out.println("Para que outros utilizadores possam cifrar/verificar ficheiros para " + username + ",");
            System.out.println("importe o certificado nas suas keystores:");
            System.out.println("  keytool -importcert -alias " + username
                    + " -keystore keystore.<outro_user> -file " + username + ".cer -noprompt");
        } else {
            System.err.println("Ocorreram erros durante a criação do utilizador '" + username + "'.");
            System.exit(1);
        }
    }

    // -------------------------------------------------------------------------
    // 1. Criar diretoria do utilizador no servidor
    // -------------------------------------------------------------------------

    private static boolean createUserDirectory(String username) {
        File baseDir = new File(STORAGE_DIR);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            System.err.println("Erro: não foi possível criar a diretoria base '" + STORAGE_DIR + "'.");
            return false;
        }

        File userDir = new File(baseDir, username);
        if (userDir.exists()) {
            System.out.println("Aviso: diretoria do utilizador já existe — " + userDir.getPath());
            return true;
        }
        if (!userDir.mkdirs()) {
            System.err.println("Erro: não foi possível criar a diretoria '" + userDir.getPath() + "'.");
            return false;
        }
        System.out.println("Diretoria criada: " + userDir.getPath());
        return true;
    }

    // -------------------------------------------------------------------------
    // 2. Gerar keystore PKCS12 com par de chaves RSA-2048
    //
    //    Equivalente ao comando manual:
    //      keytool -genkeypair -alias <user> -keyalg RSA -keysize 2048
    //              -validity 3650 -keystore keystore.<user>
    //              -storetype PKCS12 -storepass <pass> -keypass <pass>
    //              -dname "CN=<user>, OU=mySaude, O=SI, L=Lisboa, C=PT"
    // -------------------------------------------------------------------------

    private static boolean generateKeyStore(String username, String password) {
        String keystorePath = "keystore." + username;

        if (new File(keystorePath).exists()) {
            System.out.println("Aviso: keystore já existe — " + keystorePath);
            return true;
        }

        int exit = runKeytool(
            "-genkeypair",
            "-alias",     username,
            "-keyalg",    "RSA",
            "-keysize",   "2048",
            "-sigalg",    "SHA256withRSA",
            "-validity",  "3650",
            "-keystore",  keystorePath,
            "-storetype", "PKCS12",
            "-storepass", password,
            "-keypass",   password,
            "-dname",     "CN=" + username + ", OU=mySaude, O=SI, L=Lisboa, C=PT"
        );

        if (exit != 0) {
            System.err.println("Erro: keytool -genkeypair terminou com código " + exit + ".");
            return false;
        }
        System.out.println("Keystore gerada: " + keystorePath);
        return true;
    }

    // -------------------------------------------------------------------------
    // 3. Exportar certificado em formato PEM para <username>.cer
    //
    //    Equivalente ao comando manual:
    //      keytool -exportcert -alias <user> -keystore keystore.<user>
    //              -storetype PKCS12 -storepass <pass> -file <user>.cer -rfc
    // -------------------------------------------------------------------------

    private static boolean exportCertificate(String username, String password) {
        String certPath = username + ".cer";

        if (new File(certPath).exists()) {
            System.out.println("Aviso: certificado já existe — " + certPath);
            return true;
        }

        int exit = runKeytool(
            "-exportcert",
            "-alias",     username,
            "-keystore",  "keystore." + username,
            "-storetype", "PKCS12",
            "-storepass", password,
            "-file",      certPath,
            "-rfc"
        );

        if (exit != 0) {
            System.err.println("Erro: keytool -exportcert terminou com código " + exit + ".");
            return false;
        }
        System.out.println("Certificado exportado: " + certPath);
        return true;
    }

    // -------------------------------------------------------------------------
    // Auxiliar: executa o keytool com os argumentos fornecidos,
    // redireciona stdout+stderr para a consola e devolve o código de saída.
    // -------------------------------------------------------------------------

    private static int runKeytool(String... keytoolArgs) {
        String[] cmd = new String[1 + keytoolArgs.length];
        cmd[0] = "keytool";
        System.arraycopy(keytoolArgs, 0, cmd, 1, keytoolArgs.length);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            return p.waitFor();

        } catch (IOException e) {
            System.err.println("Erro ao invocar keytool: " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Execução do keytool interrompida: " + e.getMessage());
            return -1;
        }
    }
}
