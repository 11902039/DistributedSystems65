package dslab.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.AEScrypting;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;


public class MessageClient implements IMessageClient, Runnable {

    private Shell shell;
    private Config config;
    private String componentId;
    private InputStream in;
    private PrintStream out;
    private Socket DMAPSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String serverComponentId;
    private PublicKey serverPublicKey;
    private SecretKey secretKey;
    private SecureRandom random;
    private KeyGenerator keygen;
    private byte[] challenge;
    private byte[] iv;
    private int AESKey;
    private static final int NOTSTARTED = 0;
    private static final int STARTED = 1;
    private static final int CHALLENGEGIVEN = 2;
    private static final int CHALLENGEDONE = 3;
    private static final int LOGGEDIN = 4;


    private int state = NOTSTARTED;


    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.componentId = componentId;
        this.in = in;
        this.out = out;
        this.random = new SecureRandom();
        shell = new Shell(in, out);
        shell.register(this);


    }

    @Override
    public void run() {
        String answer;
        boolean started = false, challengeCorrect = false;
        byte[] challenge = new byte[32];
        //new SecureRandom().nextBytes(challenge);

        shell.out().println("Starting the client for " + config.getString("transfer.email") + "...");

        try {
            DMAPSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            reader = new BufferedReader(new InputStreamReader(DMAPSocket.getInputStream()));
            writer = new PrintWriter(DMAPSocket.getOutputStream());

            while ((answer = reader.readLine()) != null)
            {
                shell.out().println(answer);
                String[] parts = answer.split("\\s");
                switch (state) {
                    case NOTSTARTED:
                        shell.out().println("NOTSTARTED: startsecure");
                        if (answer.startsWith("ok DMAP2.0")) {
                            writer.println("startsecure");
                            writer.flush();
                            state = STARTED;
                        }
                        break;
                    case STARTED:
                        if (answer.startsWith("ok") && parts.length == 2) {
                            serverComponentId = parts[1];


                            //getting the Key based on the componentID
                            //String privateKeyFileName = "dslab21-group65-a2/keys/client/"+serverComponentId+"_pub.der";
                            String privateKeyFileName = "keys/client/"+serverComponentId+"_pub.der";
                            File serverKeyFile = new File(privateKeyFileName);
                            serverPublicKey = Keys.readPublicKey(serverKeyFile);

                            //generating the challenge, a random 32byte number, and converting it to string right after
                            challenge = new byte[32];
                            random.nextBytes(challenge);
                            String challengeString = Base64.getEncoder().encodeToString(challenge);

                            //creating the AES crypter on the client side
                            AEScrypting crypter = new AEScrypting();

                            //getting the secret key from the crypter, and converting it to String
                            secretKey = crypter.getSecretKey();
                            shell.out().println("SecretKey on client side: " + Arrays.toString(secretKey.getEncoded()));
                            String secretKeyString = Base64.getEncoder().encodeToString(secretKey.getEncoded());

                            //getting iv from the crypter, and converting to String
                            iv = crypter.getIv();
                            shell.out().println("iv on client side: " + Arrays.toString(iv));
                            String ivString = Base64.getEncoder().encodeToString(iv);

                            //generating the RSA cipher
                            Cipher cipherRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");

                            //setting it to encrypt mode
                            cipherRSA.init(Cipher.ENCRYPT_MODE,serverPublicKey);

                            String message = "ok " + challengeString + " " +  secretKeyString + " " + ivString;

                            shell.out().println("STARTED: "+message);

                            //converting to base64
                            // byte[] converted = Base64.getDecoder().decode(message);

                            //encrypting
                            byte[] encryptedMessage = cipherRSA.doFinal(message.getBytes(StandardCharsets.UTF_8));

                            //encoding the encrypted base64 into string
                            String messageToSend = Base64.getEncoder().encodeToString(encryptedMessage);


                            //TODO: Implement the cryptographic functions
                            //iv = 2137;
                            // secretKey = generateSecretKey();
                            // AESKey = generateAESKey(iv);
                            //writer.println(RSAEncryptStub("ok " + challenge + " " + secretKey + " " + iv));

                            writer.println(messageToSend);
                            writer.flush();
                            state = CHALLENGEGIVEN;
                        }
                        break;
                    case CHALLENGEGIVEN:
                        AEScrypting AEScrypter = new AEScrypting(iv,secretKey);
                        //shell.out().println("before decrypting: "+answer);
                        answer = AEScrypter.Decrypt(answer);
                        //shell.out().println("after decrypting: "+answer);
                        parts = answer.split("\\s");
                        if (answer.startsWith("ok") && parts.length == 2)
                        {
                            byte[] returnedChallenge = Base64.getDecoder().decode(parts[1]);
                            if (Arrays.equals(returnedChallenge, challenge))
                            {
                                shell.out().println("CHALLENGEGIVEN: ok");
                                AEScrypting AEScrypter2 = new AEScrypting(iv,secretKey);
                                writer.println(AEScrypter2.Encrypt("ok"));
                                writer.flush();
                                state = CHALLENGEDONE;
                            }
                        }
                        break;
                    case CHALLENGEDONE:
                        shell.out().println("CHALLENGEDONE: login " + config.getString("mailbox.user") + " " +
                                config.getString("mailbox.password"));
                        AEScrypting AEScrypter3 = new AEScrypting(iv,secretKey);
                        writer.println(AEScrypter3.Encrypt("login " + config.getString("mailbox.user") + " " +
                                config.getString("mailbox.password")));
                        writer.flush();
                        state = LOGGEDIN;
                        break;
                    case LOGGEDIN:
                        shell.out().println("LOGGEDIN");
                        AEScrypting AEScrypter4 = new AEScrypting(iv,secretKey);
                        if(AEScrypter4.Decrypt(answer).equals("ok"))
                        {
                            shell.out().println("Client is up!");
                            shell.run();
                        }
                        break;
                    default:
                }
            }

        } catch (UnknownHostException e) {
            System.out.println("Cannot connect to host: " + e.getMessage());
        } catch (SocketException e) {
            // when the socket is closed, the I/O methods of the Socket will throw a SocketException
            // almost all SocketException cases indicate that the socket was closed
            System.out.println(state +": SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            // you should properly handle all other exceptions
            throw new UncheckedIOException(e);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } finally {
            if (DMAPSocket != null && !DMAPSocket.isClosed()) {
                try {
                    DMAPSocket.close();
                } catch (IOException e) {
                    // Ignored because we cannot handle it
                }
            }
        }


    }


    @Override
    @Command
    public void inbox() {
        AEScrypting AEScrypter = new AEScrypting(iv,secretKey);
        String answer = "";
        ArrayList<String> ids = new ArrayList<>();
        try {
            writer.println(AEScrypter.Encrypt("list"));
        } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        writer.flush();
        try {
            while (!(answer = AEScrypter.Decrypt(reader.readLine())).equals("ok") && !answer.startsWith("no")) {
                String[] parts = answer.split("\\s");
                ids.add(parts[0]);
                shell.out().println(answer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
        } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if(answer.equals("no mails available"))
        {
            shell.out().println(answer);
        }
        for (String id : ids) {
            shell.out().println("message no. " + id);
            try {
            writer.println(AEScrypter.Encrypt("show " + id));
            } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            writer.flush();
            try {
                while (!(answer = AEScrypter.Decrypt(reader.readLine())).equals("ok")) {
                    shell.out().println(answer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
            } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    @Command
    public void delete(String id) {
        AEScrypting AEScrypter = new AEScrypting(iv,secretKey);
        String answer = "";
        try {
            writer.println(AEScrypter.Encrypt("delete " + id));
            writer.flush();
            answer = AEScrypter.Decrypt(reader.readLine());
        } catch (IOException e) {
            throw new UncheckedIOException("Error while using delete ", e);
        } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        shell.out().println(answer);
    }

    public String makeHash(String message) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        File hmackey = new File("dslab21-group65-a2/keys/hmac.key");
        SecretKeySpec key = Keys.readSecretKey(hmackey);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] hashed = mac.doFinal(messageBytes);
        String output = Base64.getEncoder().encodeToString(hashed);
        return output;
    }

    public String hashStub (String message)
    {
        return message;
    }

    public String AESEncryptStub(String message)
    {
        return message;
    }

    public String AESDecryptStub(String message)
    {
        return message;
    }

    public String RSAEncryptStub(String message)
    {
        return message;
    }


    @Override
    @Command
    public void verify(String id) {
        AEScrypting AEScrypter = new AEScrypting(iv,secretKey);
        String answer;
        StringBuilder messageBuilder = new StringBuilder(100);
        try {
            writer.println(AEScrypter.Encrypt("show " + id));
        } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        writer.flush();
        try {
            while (!(answer = AEScrypter.Decrypt(reader.readLine())).equals("ok")) {
                //shell.out().println(answer);
                String[] parts = answer.split("\\s");
                if(answer.startsWith("data") || answer.startsWith("subject")) {
                    for (int i = 1; i < parts.length; i++) {
                        messageBuilder.append(parts[i]);
                        if(i + 1 != parts.length) {
                            messageBuilder.append(" ");
                        }
                        //shell.out().println(messageBuilder.toString());
                    }
                }
                else if (!answer.startsWith("hash"))
                {
                    messageBuilder.append(parts[1]);
                    messageBuilder.append("\n");
                }
                if(answer.startsWith("hash"))
                {
                    if(parts.length > 1) {
                        //TODO: implement the hash function
                        if (parts[1].equals(makeHash(messageBuilder.toString()))) {
                            shell.out().println("ok");
                        }
                    }
                    else {
                       shell.out().println("error");
                   }

                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while using show ", e);
        } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

    }

    @Override
    @Command
    public void msg(String to, String subject, String data) {
        String answer;
        int count = 0;
        try {
            Socket DMTPSocket = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            BufferedReader readerDMTP = new BufferedReader(new InputStreamReader(DMTPSocket.getInputStream()));
            PrintWriter writerDMTP = new PrintWriter(DMTPSocket.getOutputStream());
            while ((answer = readerDMTP.readLine()) != null && count < 6) {
                //shell.out().println(answer);
                if (answer.equals("ok DMTP2.0"))
                {
                    writerDMTP.println("begin");
                    writerDMTP.flush();
                }
                else if (answer.startsWith("ok"))
                {
                    switch (count) {
                        case 0:
                            writerDMTP.println("from " + config.getString("transfer.email"));
                            writerDMTP.flush();
                            count++;
                            break;
                        case 1:
                            writerDMTP.println("to " + to);
                            writerDMTP.flush();
                            count++;
                            break;
                        case 2:
                            writerDMTP.println("subject " + subject);
                            writerDMTP.flush();
                            count++;
                            break;
                        case 3:
                            writerDMTP.println("data " + data);
                            writerDMTP.flush();
                            count++;
                            break;
                        case 4:
                            //TODO: implement the hash function;
                            String msg = String.join("\n", config.getString("transfer.email"), to, subject, data);
                            writerDMTP.println("hash " + makeHash(msg));
                            writerDMTP.flush();
                            count++;
                            break;
                        case 5:
                            writerDMTP.println("send");
                            writerDMTP.flush();
                            count++;
                            break;
                        default:
                    }
                }
                else
                {
                    shell.out().println("error of DMTP");
                }
            }
            shell.out().println(answer + " message sent");
            try {
                DMTPSocket.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        } catch (UnknownHostException e) {
            System.out.println("Cannot connect to host: " + e.getMessage());
        } catch (SocketException e) {
            // when the socket is closed, the I/O methods of the Socket will throw a SocketException
            // almost all SocketException cases indicate that the socket was closed
            System.out.println("SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            // you should properly handle all other exceptions
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

    }


    @Override
    @Command
    public void shutdown(){
        AEScrypting AEScrypter = new AEScrypting(iv, secretKey);
        try {
            writer.println(AEScrypter.Encrypt("quit"));
        } catch (NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        writer.flush();
        try {
            DMAPSocket.close();
        } catch (IOException e)
        {
            throw new UncheckedIOException("Error while closing the socket ", e);
        }
        Thread.currentThread().interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
