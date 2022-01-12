package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Message.Message;
import dslab.mailbox.tcp.ListenerThread;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {

    private Config config;
    private ServerSocket serverSocketDMTP;
    private ServerSocket serverSocketDMAP;
    private ListenerThread dmtpThread;
    private ListenerThread dmapThread;
    private String domain;
    private Config userConfig;
    private String componentID;

    private INameserverRemote rootNameServer;

    private Shell shell;

    ConcurrentHashMap<Integer, Message> messages = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, List<Integer>> users = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, List<String>> ids = new ConcurrentHashMap<>();


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentID = componentId;
        this.config = config;
        this.userConfig = new Config(config.getString("users.config"));
        this.domain = config.getString("domain");
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "---> ");
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

        Set<String> userlist = this.userConfig.listKeys();
        for (String user: userlist) {
            users.put(user,new LinkedList<>());
        }

        try {
            serverSocketDMTP = new ServerSocket(config.getInt("dmtp.tcp.port"));

            this.dmtpThread = new ListenerThread(serverSocketDMTP, this, true, componentID);
            this.dmtpThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }

        try {
            serverSocketDMAP = new ServerSocket(config.getInt("dmap.tcp.port"));

            this.dmapThread = new ListenerThread(serverSocketDMAP, this, false, componentID);
            this.dmapThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }


        try {
            if (rootNameServer != null) {
                rootNameServer.registerMailboxServer(this.domain, this.serverSocketDMTP.getLocalSocketAddress().toString().split("/")[1]);
            }
        } catch (AlreadyRegisteredException e) {
            System.err.println(e.getMessage());
        } catch (InvalidDomainException e){
            System.err.println(e.getMessage());
        } catch (RemoteException e) {
            System.err.println(e.getMessage());
        }

        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        this.dmapThread.shutdown();
        this.dmtpThread.shutdown();
        try {
            if(serverSocketDMTP!=null)
                serverSocketDMTP.close();
        } catch (IOException e) {
            System.err.println("Error while closing server socket: " + e.getMessage());
        }
        try {
            if (serverSocketDMAP!=null)
                serverSocketDMAP.close();
        } catch (IOException e) {
            System.err.println("Error while closing server socket: " + e.getMessage());
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }

    public String getDomain(){
        return this.domain;
    }

    /**
     * Checks whether a user exists in the database.
     *
     * @param name to be checked
     * @return true if the user exists and false otherwise.
     */
    public boolean checkUser(String name){
        return this.userConfig.containsKey(name);
    }

    /**
     * Checks whether or not a user is in the database, and checks the given password.
     *
     * @param name of the user
     * @param password of the user
     * @return 0 if the user wasn't found, 1 if the password is wrong and 2 if both is correct
     */
    public int checkLogin(String name, String password){
        if (!this.userConfig.containsKey(name))
            return 0;
        if(!this.userConfig.getString(name).equals(password))
            return 1;
        return 2;
    }

    /**
     * Saves a given message in the list and associates it with its users.
     *
     * @param message to be saved
     */
    public void save(Message message){
        int id;
        synchronized (this){
            int msgnr = this.messages.size();
            id = (int)(Math.random()*(double)msgnr*5.d); //calculate a random id

            while (true) {
                if (this.messages.containsKey(id)) //is id already in use?
                    id = (int)(Math.random()*(double)msgnr*5.d); //calculate new id
                else
                    break;
            }

            this.messages.put(id,message); //save message with id
            this.ids.put(id,new LinkedList<>());
        }

        List<String> recips = message.getRecipients();
        for (String recip: recips) {
            String user = recip.split("@")[0];
            String domain = recip.split("@")[1];

            if(domain.equals(this.domain)){ //if domain correct, save mail for current user
                synchronized (this) {
                    List<Integer> userIds = users.get(user); //get List of associated mails
                    userIds.add(id);
                    users.replace(user, userIds); //add id to users messages
                    List<String> usernames = ids.get(id); //get List of associated users
                    usernames.add(user);
                    ids.replace(id, usernames); //add user to id
                }
            }
        }
    }

    /**
     * Deletes a message with the given id for the logged in user.
     *
     * @param id of the message
     * @param user who is logged in
     * @return true if the message was removed and false otherwise.
     */
    public boolean delete(int id, String user) {
        List<Integer> userIds = this.users.get(user);
        List<String> usernames = this.ids.get(id);
        if (!userIds.contains(id) || !usernames.contains(user))
            return false;

        int a;
        String b;
        synchronized (this) { //make sure nothing interferes with the changing of ids
            a = userIds.remove(userIds.indexOf(id));
            b = usernames.remove(usernames.indexOf(user));

            if (usernames.isEmpty())
                this.messages.remove(id); //if noone can access the message anymore, delete it
        }

        return a==id && b.equals(user);
    }

    /**
     * Shows all Messages associated with the user
     *
     * @param user who is logged in
     * @return a HashMap of Messages and their ids or null if there are none
     */
    public HashMap<Integer,Message> showMessages(String user) {
        HashMap<Integer,Message> messages = new HashMap<>();

        List<Integer> userIds = this.users.get(user);

        for (int i: userIds) {
            messages.put(i,this.messages.get(i));
        }
        if(userIds.isEmpty())
            return null;

        return messages;
    }

    /**
     * Returns the message with the given id
     *
     * @param id of the message
     * @param user that is logged in
     * @return the Message with the given id or null if the user has no such message
     */
    public Message showMessage(int id, String user){
        List<Integer> userIds = this.users.get(user);
        List<String> usernames = this.ids.get(id);
        if (!userIds.contains(id) || !usernames.contains(user))
            return null;

        return this.messages.get(id);
    }


}
