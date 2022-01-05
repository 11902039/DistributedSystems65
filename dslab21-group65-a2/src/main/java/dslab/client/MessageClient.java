package dslab.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MessageClient implements IMessageClient, Runnable {

    private Shell shell;
    private Config config;
    private String componentId;
    private InputStream in;
    private PrintStream out;
    private Socket DMAPSocket;
    BufferedReader reader;
    PrintWriter writer;

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
        String request;
        try {
            DMAPSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            reader = new BufferedReader(new InputStreamReader(DMAPSocket.getInputStream()));
            writer = new PrintWriter(DMAPSocket.getOutputStream());

            while ((request = reader.readLine()) != null) {
                System.out.println("Server sent the following request: " + request);
                String[] parts = request.split("\\s");
                if (request.startsWith("OK DMAP2.0"))
                {
                    writer.println("startsecure");
                }
                //comunication goes on
            }

            System.out.println("Client is up!");
            shell.run();

        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }


    }

    @Override
    public void inbox() {

    }

    @Override
    public void delete(String id) {

    }

    @Override
    public void verify(String id) {

    }

    @Override
    public void msg(String to, String subject, String data) {
        String request;
        try {
            Socket DMTPSocket = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            BufferedReader readerDMTP = new BufferedReader(new InputStreamReader(DMTPSocket.getInputStream()));
            PrintWriter writerDMTP = new PrintWriter(DMTPSocket.getOutputStream());

            while ((request = reader.readLine()) != null) {
                System.out.println("Server sent the following request: " + request);
                String[] parts = request.split("\\s");
                if (request.startsWith("OK DMTP2.0"))
                {
                    writerDMTP.println("begin");
                }
                //comunication goes on
            }

            System.out.println("Client is up!");
            shell.run();

        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }
    }

    @Override
    public void shutdown() throws IOException {
        writer.println("logout");
        DMAPSocket.close();
        Thread.currentThread().interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
