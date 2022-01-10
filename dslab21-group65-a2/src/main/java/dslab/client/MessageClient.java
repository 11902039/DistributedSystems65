package dslab.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.Config;
import java.security.SecureRandom;
import java.util.ArrayList;


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
    private int secretKey;
    private int AESKey;
    private int iv;
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

    @Override
    public void run() {
        String answer;
        boolean started = false, challengeCorrect = false;
        //byte[] challenge = new byte[32];
        //new SecureRandom().nextBytes(challenge);
        String challenge = "xd";

        try {
            DMAPSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            reader = new BufferedReader(new InputStreamReader(DMAPSocket.getInputStream()));
            writer = new PrintWriter(DMAPSocket.getOutputStream());
            shell.run();
            while ((answer = reader.readLine()) != null)
            {
                shell.out().println(answer);
                String[] parts = answer.split("\\s");
                switch (state) {
                    case NOTSTARTED:
                        if (answer.startsWith("ok DMAP2.0")) {
                            writer.println("startsecure");
                            state = STARTED;
                        }
                        break;
                    case STARTED:
                        if (answer.startsWith("ok") && parts.length == 2) {
                            serverComponentId = parts[1];

                            //TODO: Implement the cryptographic functions
                            iv = 2137;
                            // secretKey = generateSecretKey();
                            // AESKey = generateAESKey(iv);
                            writer.println(RSAEncryptStub("ok " + challenge + " " + secretKey + " " + iv));
                            state = CHALLENGEGIVEN;
                            break;
                        }
                    case CHALLENGEGIVEN:
                        answer = AESDecryptStub(answer);
                        parts = answer.split("\\s");
                        if (answer.startsWith("ok") && parts.length == 2) {
                            if (parts[1].equals(challenge))
                            {
                                writer.println(AESEncryptStub("ok"));
                                state = CHALLENGEDONE;
                                break;
                            }
                        }
                    case CHALLENGEDONE:
                        writer.println(AESEncryptStub("login " + config.getString("mailbox.user") + " " +
                                config.getString("mailbox.password")));
                        state = LOGGEDIN;
                        break;
                    default:
                        shell.out().println("Something went wrong during the handshake, the last state was " + state);
                }
            }
            shell.out().println("Client is up!");
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }


    }


    @Override
    public void inbox() {
        String answer;
        ArrayList<String> ids = new ArrayList<>();
        writer.println("list");
        try {
            while (!(answer = reader.readLine()).equals("ok")) {
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
                while (!(answer = reader.readLine()).equals("ok")) {
                    shell.out().println(answer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
            }
        }
        writer.println("");

    }

    @Override
    public void delete(String id) {
        String answer;
        try {
            writer.println("delete " + id);
            answer = reader.readLine();
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
    public void verify(String id) {
        String answer;
        StringBuilder messageBuilder = new StringBuilder(100);
        writer.println("show " + id);
        try {
            while (!(answer = reader.readLine()).equals("ok")) {
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
    public void shutdown(){
        writer.println("logout");
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
