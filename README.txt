# mySaude — Trabalho 2

## Ficheiros principais

- `mySaude.java` — cliente
- `mySaudeServer.java` — servidor TLS com autenticação, controlo de acesso e GET_CERT
- `criarUser.java` — criação de utilizadores no servidor

Não é necessário entregar `Cifra.java` e `Descifra.java`; são ficheiros de teste/laboratório e não fazem parte da solução principal.

---

## 1. Compilar

```powershell
javac criarUser.java mySaudeServer.java mySaude.java
```

---

## 2. Criar keystore do servidor

```powershell
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.server -storepass changeit -keypass changeit -dname "CN=localhost"
```

Exportar certificado do servidor:

```powershell
keytool -exportcert -alias server -keystore keystore.server -storepass changeit -rfc -file server.cer
```

Criar truststore para o cliente confiar no servidor:

```powershell
keytool -importcert -alias server -file server.cer -keystore truststore.client -storepass changeit -noprompt
```

---

## 3. Criar keystores dos utilizadores

Exemplo de médico:

```powershell
keytool -genkeypair -alias maria -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.maria -storepass passMaria -keypass passMaria -dname "CN=maria"
keytool -exportcert -alias maria -keystore keystore.maria -storepass passMaria -rfc -file maria.cer
```

Exemplo de utente:

```powershell
keytool -genkeypair -alias bob -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.bob -storepass passBob -keypass passBob -dname "CN=bob"
keytool -exportcert -alias bob -keystore keystore.bob -storepass passBob -rfc -file bob.cer
```

---

## 4. Criar utilizadores no servidor

Usar sempre a mesma password MAC durante os testes, por exemplo:

```text
macpass
```

Criar médico:

```powershell
java criarUser maria medico passMaria -f maria.cer
```

Criar utente:

```powershell
java criarUser bob utente passBob -f bob.cer
```

Isto cria:

- linhas no ficheiro `users`
- `mySaude.mac`
- diretórios `server_storage/maria` e `server_storage/bob`
- certificados em `keystore.users`

---

## 5. Arrancar servidor

```powershell
java mySaudeServer 23456
```

Quando pedir a password MAC, escrever:

```text
macpass
```

---

## 6. Comandos de cliente

Em todos os comandos com servidor, usar truststore:

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude ...
```

### Enviar ficheiro — deve funcionar porque `maria` é medico

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u maria -p passMaria -e teste.txt -t bob
```

### Enviar ficheiro — deve falhar porque `bob` é utente

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u bob -p passBob -e teste.txt -t maria
```

### Receber ficheiro

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u bob -p passBob -r teste.txt
```

### Cifrar e enviar

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u maria -p passMaria -ce teste.txt -t bob
```

### Receber e decifrar

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u bob -p passBob -rd teste.txt
```

### Assinar e enviar

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u maria -p passMaria -ae teste.txt -t bob
```

### Receber e verificar assinatura

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u bob -p passBob -rv teste.txt -t maria
```

### Envelope seguro: assinar, cifrar e enviar

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u maria -p passMaria -ace teste.txt -t bob
```

### Receber, decifrar e verificar

```powershell
java -Djavax.net.ssl.trustStore=truststore.client -Djavax.net.ssl.trustStorePassword=changeit mySaude -s localhost:23456 -u bob -p passBob -rdv teste.txt -t maria
```

---

## 7. Testes importantes para a apresentação

1. Password errada no cliente deve dar erro de autenticação.
2. Utilizador `utente` a tentar enviar ficheiros deve falhar.
3. Médico a enviar ficheiros deve funcionar.
4. `users` alterado manualmente deve fazer o servidor recusar arrancar por MAC inválido.
5. Cliente sem certificado do destinatário deve pedir o certificado ao servidor por `GET_CERT`.
6. Tentativa de enviar ficheiro repetido ou variante repetida deve dar erro.

---

## 8. Limpeza antes da entrega

Não entregar ficheiros gerados nos testes, a menos que o professor peça:

- `*.class`
- `*.cifrado`
- `*.chave.*`
- `*.assinatura.*`
- `*.assinado`
- `*.envelope`
- ficheiros temporários de teste

Confirmar com o grupo se devem ou não entregar keystores/certificados de exemplo.
