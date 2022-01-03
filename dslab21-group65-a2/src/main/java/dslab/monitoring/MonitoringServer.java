package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.util.HashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.monitoring.udp.MListenerThread;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    private Config config;
    private DatagramSocket datagramSocket;
    private MListenerThread thread;

    private Shell shell;

    private HashMap<String,Integer> addresses;
    private HashMap<String,Integer> servers;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "---> ");
        this.config = config;
        this.addresses = new HashMap<>();
        this.servers = new HashMap<>();
    }

    @Override
    public void run() {
        try {
            // constructs a datagram socket and binds it to the specified port
            datagramSocket = new DatagramSocket(config.getInt("udp.port"));

            // create a new thread to listen for incoming packets
            this.thread = new MListenerThread(datagramSocket, this);
            this.thread.start();
        } catch (IOException e) {
            throw new RuntimeException("Cannot listen on UDP port.", e);
        }
        shell.run();
    }

    @Override
    @Command
    public void addresses() {
        for (String key: addresses.keySet()) {
            shell.out().println(key + " " + addresses.get(key));
        }
    }

    @Override
    @Command
    public void servers() {
        for (String key: servers.keySet()) {
            shell.out().println(key + " " + servers.get(key));
        }
    }

    public void addMail(String server, String address) {
        if(!this.addresses.containsKey(address)){
            this.addresses.put(address, 1);
        } else {
            int oldVal = this.addresses.get(address);
            this.addresses.replace(address,oldVal,oldVal+1);
        }

        if(!this.servers.containsKey(server)){
            this.servers.put(server,1);
        } else {
            int oldVal = this.servers.get(server);
            this.servers.replace(server,oldVal,oldVal+1);
        }
    }

    @Override
    @Command
    public void shutdown() {
        this.thread.shutdown();
        if (datagramSocket != null) {
            datagramSocket.close();
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
