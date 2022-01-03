package dslab.nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.mailbox.IMailboxServer;
import dslab.util.Config;

public class Nameserver implements INameserver, INameserverRemote {

    private Shell shell;

    private String domain;

    private Registry registry;
    private Config config;

    private ConcurrentHashMap<String,INameserverRemote> childServers;
    private ConcurrentHashMap<String,String> mailboxes;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "---> ");
        this.domain = config.getString("domain");
        this.config = config;
    }

    @Override
    public void run() {
        try {
            if(this.domain == null) { //only do this for root server
                // create and export the registry instance on localhost at the specified port
                registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
                // create a remote object of this server object
                INameserverRemote remote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
                // bind the obtained remote object on specified binding name in the registry
                registry.bind(config.getString("root_id"), remote);
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Error while starting server.", e);
        } catch (AlreadyBoundException e) {
            throw new RuntimeException("Error while binding remote object to registry.", e);
        }

        /* Comment??
        String name;
        if(domain == null){
            name = "root nameserver";
        } else {
            name = domain + " nameserver";
        }
        shell.out().println("# " + name);
         */

        shell.run();
    }

    @Override
    @Command
    public void nameservers() {
        int counter = 1;
        for (String server: childServers.keySet()) {
            shell.out().println(counter + ". " + server);
            counter++;
        }

        for (INameserverRemote server: childServers.values()) {
            //somehow recursively call nameservers?
        }
    }

    @Override
    @Command
    public void addresses() {
        int counter = 1;
        for (String server: mailboxes.keySet()) {
            shell.out().println(counter + ". " + server + ":" + mailboxes.get(server));
            counter++;
        }

        for (INameserverRemote server: childServers.values()) {
            //somehow recursively call nameservers?
        }
    }

    @Override
    @Command
    public void shutdown() {
        try {
            // unexport the previously exported remote object
            UnicastRemoteObject.unexportObject(this, true);
            UnicastRemoteObject.unexportObject(registry,true);
        } catch (NoSuchObjectException e) {
            System.err.println("Error while unexporting object: " + e.getMessage());
        }

        try {
            // unbind the remote object so that a client can't find it anymore
            registry.unbind(config.getString("root_id"));
        } catch (Exception e) {
            System.err.println("Error while unbinding object: " + e.getMessage());
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        shell.out().println(LocalDate.now() + ": Nameserver for " + zone + " requested by transfer server");
        return childServers.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        shell.out().println(LocalDate.now() + ": Address for Mailbox Server" + username + " requested by transfer server");
        return mailboxes.get(username);
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String[] parts = domain.split(".");

        //still a dot in the domain
        if(parts.length > 1){
            INameserverRemote child;

            synchronized (this) {
                child = this.childServers.get(parts[parts.length - 1]);
            }

            if (child == null){ //no such child exists
                throw new InvalidDomainException("The intermediary domain of the nameserver " + parts[parts.length-1] + " does not exist!");
            }

            String restDomain = parts[0];
            //invalid double dots in domain
            if("".equals(parts[0]))
                throw new InvalidDomainException("The domain of the nameserver is invalid!");
            for (int i = 1; i < parts.length-1 ; i++) {
                restDomain += "." + parts[i];

                //invalid double dots in domain
                if("".equals(parts[i]))
                    throw new InvalidDomainException("The domain of the nameserver is invalid!");
            }

            //go to child
            child.registerNameserver(restDomain,nameserver);
        }

        //nameserver exists
        if (childServers.get(domain) != null) {
            throw new AlreadyRegisteredException("The nameserver " + domain + " already exists!");
        }

        synchronized (this) {
            //save new child nameserver
            this.shell.out().println(LocalDate.now() + ": Registering Nameserver " + domain + " for zone "+ this.domain);
            this.childServers.put(domain, nameserver);
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String[] parts = domain.split(".");

        //still a dot in the domain
        if(parts.length > 1){
            INameserverRemote child;

            synchronized (this) {
                child = this.childServers.get(parts[parts.length - 1]);
            }

            if (child == null){ //no such child exists
                throw new InvalidDomainException("The domain of the mailbox server does not exist! Nameserver "+ parts[parts.length-1] + " not found.");
            }

            String restDomain = parts[0];
            //invalid double dots in domain
            if("".equals(parts[0]))
                throw new InvalidDomainException("The domain of the mailbox server is invalid!");

            for (int i = 1; i < parts.length-1 ; i++) {
                restDomain += "." + parts[i];

                //invalid double dots in domain
                if("".equals(parts[i]))
                    throw new InvalidDomainException("The domain of the mailbox server is invalid!");
            }
            child.registerMailboxServer(restDomain,address);
        }

        //root nameserver case
        if (this.domain == null){
            throw new InvalidDomainException("Registering in the root nameserver is not allowed!");
        }

        //mailbox already saved
        if (this.mailboxes.get(domain)!= null)
            throw new AlreadyRegisteredException("The mailbox server " + domain + "already exists in the nameserver " + this.domain);

        synchronized (this) {
            //save new mailbox server

            this.shell.out().println(LocalDate.now() + ": Registering Mailbox Server " + domain + " for nameserver "+ this.domain);

            this.mailboxes.put(domain, address);
        }
    }
}
