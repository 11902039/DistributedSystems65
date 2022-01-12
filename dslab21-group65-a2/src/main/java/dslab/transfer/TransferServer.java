package dslab.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.SQLOutput;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Message.Message;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.nameserver.Nameserver;
import dslab.transfer.tcp.ClientThread;
import dslab.transfer.tcp.TListenerThread;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {

    private Config config;
    private Config domainConfig;
    private ServerSocket serverSocket;
    private boolean active = true;

    private ExecutorService ThreadPool;
    private TListenerThread thread;
    private ShellListenerThread shellthread;
    private DatagramSocket dataSocket;
    private INameserverRemote rootNameServer;

    private Shell shell;

    private LinkedBlockingQueue<Message> messages;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.domainConfig = new Config("domains.properties");
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "---> ");
        messages = new LinkedBlockingQueue<>();
    }

    @Override
    public void run() {

        try {
            Registry registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));

            rootNameServer = (INameserverRemote) registry.lookup(config.getString("root_id"));
        } catch (RemoteException e) {
            System.err.println("Error while trying to locate Registry of Root Nameserver");
        } catch (NotBoundException e) {
            System.err.println("Error while trying to lookup Root Nameserver");
        }

        try {
            this.dataSocket = new DatagramSocket();
            this.serverSocket = new ServerSocket(config.getInt("tcp.port"));
            this.thread = new TListenerThread(serverSocket, this);
            this.thread.start();
            this.shellthread = new ShellListenerThread(this.shell);
            this.shellthread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }

        this.ThreadPool = Executors.newCachedThreadPool();
        while(active) { //constant checking for messages
            if (!messages.isEmpty()) {
                Message msg = null;
                try {
                    msg = messages.take(); //get first message
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(msg != null) {
                    List<String> errorDoms = new LinkedList<>();
                    List<String> recips = msg.getRecipients();
                    Set<String> domains = new HashSet<>();
                    for (String rec : recips) { //check the domains, put into known and unknown (error) domains
                        String[] parts = rec.split("@");
                        if (parts.length > 1)
                            domains.add(parts[1]);
                    }

                    for (String dom : domains) { //lookup, create threads for each domain

                        String[] domainParts = dom.split("\\.");

                        INameserverRemote nsServer = rootNameServer;
                        String host = this.domainConfig.getString(dom);

                        try {
                            for (int i = domainParts.length-1; i > 0; i--) {
                                if (nsServer != null)
                                    nsServer = nsServer.getNameserver(domainParts[i]);
                            }

                            if (nsServer == null)
                                errorDoms.add(dom);
                            else
                                host = nsServer.lookup(domainParts[0]);

                        } catch (RemoteException e) {
                            System.err.println("An error occurred while communicating with the server: " + e.getMessage());
                        }

                        if(nsServer != null) {
                            String port = host.split(":")[1];
                            host = host.split(":")[0];
                            try {
                                Thread t = new ClientThread(msg, this, new Socket(host, Integer.parseInt(port)));
                                ThreadPool.submit(t);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (!errorDoms.isEmpty()){ //if there is an unknown domain, send error message
                        String ip = this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort();
                        if(!msg.getSender().equals(("mailer@" + ip))) {
                            String data = "Dear User: The ";
                            if(errorDoms.size() >1){
                                data += "recipients ";
                            }
                            else
                                data += "recipient ";
                            for (String err: errorDoms) {
                                data += err + " ";
                            }
                            data += "could not be matched to any existing domains. If there were other recipients, the " +
                                    "message was forwarded to their respective Mailbox Servers. The content of the message " +
                                    "was: " + msg.getData();
                            this.errorMessager(msg.getSender(),data,msg.getSubject()); //save the error message
                        }
                    }
                }
            }
        }
    }

    public void errorMessager(String recipient, String data, String subject){
        if(!recipient.equals("mailer@" + this.config.getString("tcp.port"))) {
            Message errorMail = new Message();
            errorMail.setSender("mailer@" + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort());
            errorMail.setSubject("Error while delivering message \"" + subject + "\"");
            errorMail.addRecipients(recipient);
            errorMail.setData(data);
            this.save(errorMail);
        }
    }

    @Override
    @Command
    public void shutdown() {
        this.thread.shutdown();
        ThreadPool.shutdown();
        active = false;
        try {
            if (serverSocket!=null)
                serverSocket.close();
        } catch (IOException e){
            System.err.println("Error while closing server socket: " + e.getMessage());
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }


    public void sendPacket(String text){
        try {
            byte[] buffer = text.getBytes();
            DatagramPacket packet;

            packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(this.config.getString("monitoring.host")),
                    this.config.getInt("monitoring.port")); //create DatagramPacket

            dataSocket.send(packet); //send packet
        } catch (UnknownHostException e) {
            System.out.println("Cannot connect to host: " + e.getMessage());
        } catch (SocketException e) {
            System.out.println("SocketException: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(Message message){
        try {
            this.messages.put(message);
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }

}
