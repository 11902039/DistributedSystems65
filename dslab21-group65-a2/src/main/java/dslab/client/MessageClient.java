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
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
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
        shell = new Shell(in, out);
        shell.register(this);

    }

    private SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128); // The AES key size in number of bits
        return generator.generateKey();
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
                            shell.out().println("STARTED: ok " + challenge + " " + secretKey + " " + iv);

                            //getting the Key based on the componentID
                            File serverKeyFile = new File("keys/client/"+serverComponentId+"_pub.der");
                            serverPublicKey = Keys.readPublicKey(serverKeyFile);

                            //generating the challenge, a random 32byte number
                            challenge = new byte[32];
                            random.nextBytes(challenge);

                            AEScrypting crypter = new AEScrypting();

                            //generating the secret key
                            secretKey = crypter.getSecretKey();

                            //generating the iv, a random 16byte number
                            iv = crypter.getIv();

                            //generating the RSA cipher
                            Cipher cipherRSA = Cipher.getInstance("RSA/ECB/PKCS5Padding");

                            //setting it to encrypt mode
                            cipherRSA.init(Cipher.ENCRYPT_MODE,serverPublicKey);

                            String message = "ok " + challenge + " " +  secretKey.getEncoded() + " " + iv;

                            String messageToSend = crypter.Encrypt(message);


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
                        answer = AESDecryptStub(answer);
                        parts = answer.split("\\s");
                        if (answer.startsWith("ok") && parts.length == 2)
                        {
                            if (parts[1].equals(challenge))
                            {
                                shell.out().println("CHALLENGEGIVEN: ok");
                                writer.println(AESEncryptStub("ok"));
                                writer.flush();
                                state = CHALLENGEDONE;
                            }
                        }
                        break;
                    case CHALLENGEDONE:
                        shell.out().println("CHALLENGEDONE: login " + config.getString("mailbox.user") + " " +
                                config.getString("mailbox.password"));
                        writer.println(AESEncryptStub("login " + config.getString("mailbox.user") + " " +
                                config.getString("mailbox.password")));
                        writer.flush();
                        state = LOGGEDIN;
                        break;
                    case LOGGEDIN:
                        shell.out().println("LOGGEDIN");
                        if(AESDecryptStub(answer).equals("ok"))
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
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
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
        String answer;
        ArrayList<String> ids = new ArrayList<>();
        writer.println("list");
        try {
            while (!(answer = AESDecryptStub(reader.readLine())).equals("ok")) {
                String[] parts = answer.split("\\s");
                ids.add(parts[0]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
        }
        for (String id : ids) {
            shell.out().println("message no. " + id);
            writer.println("show " + id);
            try {
                while (!(answer = AESDecryptStub(reader.readLine())).equals("ok")) {
                    shell.out().println(answer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
            }
        }
        writer.println("");

    }

    @Override
    @Command
    public void delete(String id) {
        String answer;
        try {
            writer.println(AESEncryptStub("delete " + id));
            answer = AESDecryptStub(reader.readLine());
        } catch (IOException e) {
            throw new UncheckedIOException("Error while using delete ", e);
        }
        shell.out().println(answer);
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

    public String RSADecryptStub(String message)
    {
        return message;
    }

    @Override
    @Command
    public void verify(String id) {
        String answer;
        StringBuilder messageBuilder = new StringBuilder(100);
        writer.println(AESEncryptStub("show " + id));
        try {
            while (!(answer = AESDecryptStub(reader.readLine())).equals("ok")) {
                String[] parts = answer.split("\\s");
                if(answer.startsWith("data")) {
                    for (int i = 1; i < parts.length; i++) {
                        messageBuilder.append(parts[i]);
                        if(i + 1 != parts.length) {
                            messageBuilder.append(" ");
                        }
                    }
                }
                if(answer.startsWith("hash"))
                {
                    //TODO: implement the hash function
                   if(parts[1].equals(hashStub(messageBuilder.toString())))
                    {
                        shell.out().println("ok");
                    }
                    else {
                       shell.out().println("error");
                   }

                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while using show ", e);
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

            while ((answer = reader.readLine()) != null) {
                shell.out().println(answer);
                String[] parts = answer.split("\\s");
                if (answer.startsWith("ok DMTP2.0"))
                {
                    writerDMTP.println("begin");
                }
                else if (answer.startsWith("ok"))
                {
                    switch (count) {
                        case 0:
                            writerDMTP.println("from" + subject);
                            count++;
                            break;
                        case 1:
                            writerDMTP.println("to" + subject);
                            count++;
                            break;
                        case 2:
                            writerDMTP.println("data" + data);
                            count++;
                            break;
                        case 3:
                            //TODO: implement the hash function
                            writerDMTP.println("hash" + hashStub(data));
                            count++;
                            break;
                        case 4:
                            writerDMTP.println("send");
                            count++;
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }
    }

    @Override
    @Command
    public void shutdown(){
        writer.println(AESEncryptStub("quit"));
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
