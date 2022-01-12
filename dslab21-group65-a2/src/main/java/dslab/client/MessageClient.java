package dslab.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

import java.net.SocketException;
import java.net.UnknownHostException;
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
                            //TODO: Implement the cryptographic functions
                            iv = 2137;
                            // secretKey = generateSecretKey();
                            // AESKey = generateAESKey(iv);
                            writer.println(RSAEncryptStub("ok " + challenge + " " + secretKey + " " + iv));
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
        writer.flush();
        shell.out().println("inbox: list");
        try {
            while (!(answer = AESDecryptStub(reader.readLine())).equals("ok") && !answer.startsWith("no")) {
                String[] parts = answer.split("\\s");
                ids.add(parts[0]);
                shell.out().println(answer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
        }
        if(answer.equals("no mails available"))
        {
            shell.out().println(answer);
        }
        for (String id : ids) {
            shell.out().println("message no. " + id);
            writer.println("show " + id);
            writer.flush();
            shell.out().println("inbox: show " + id);
            try {
                while (!(answer = AESDecryptStub(reader.readLine())).equals("ok")) {
                    shell.out().println(answer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Error while reading the mailbox server answer to list", e);
            }
        }
    }

    @Override
    @Command
    public void delete(String id) {
        String answer;
        try {
            writer.println(AESEncryptStub("delete " + id));
            writer.flush();
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
        writer.flush();
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
        shell.out().println("GOTTA msg " + to + " " + subject + " " + data);
        String answer;
        int count = 0;
        try {
            Socket DMTPSocket = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            BufferedReader readerDMTP = new BufferedReader(new InputStreamReader(DMTPSocket.getInputStream()));
            PrintWriter writerDMTP = new PrintWriter(DMTPSocket.getOutputStream());
            shell.out().println("Sockety things done");
            while ((answer = readerDMTP.readLine()) != null && count < 6) {
                shell.out().println(answer);
                if (answer.equals("ok DMTP2.0"))
                {
                    shell.out().println("sending: begin");
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
                            //TODO: implement the hash function
                            writerDMTP.println("hash " + hashStub(data));
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
            }
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
        }

    }


    @Override
    @Command
    public void shutdown(){
        writer.println(AESEncryptStub("quit"));
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
