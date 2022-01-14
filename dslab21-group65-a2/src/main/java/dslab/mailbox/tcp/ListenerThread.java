package dslab.mailbox.tcp;


import dslab.DMTP.IListenerThread;
import dslab.DMTP.dmtpThread;
import dslab.Message.Message;
import dslab.mailbox.MailboxServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListenerThread extends Thread implements IListenerThread {

    private final ServerSocket serverSocket;
    private boolean listening;
    private final MailboxServer mailboxServer;
    private final boolean DMTP; //true if DMTP Listener and false if DMAP
    private ExecutorService ThreadPool;
    private ConcurrentLinkedQueue<Socket> sockets = new ConcurrentLinkedQueue<>();
    private String componentID;

    public ListenerThread(ServerSocket serverSocket, MailboxServer mailboxServer, boolean DMTP, String componentID) {
        this.serverSocket = serverSocket;
        listening = true;
        this.DMTP = DMTP;
        this.mailboxServer = mailboxServer;
        this.componentID = componentID;
    }

    @Override
    public void run() {
        ThreadPool = Executors.newCachedThreadPool();
        try {
            while (listening) { //listening for clients
                Thread t;
                if (DMTP)
                    t = new dmtpThread(serverSocket.accept(), this);
                else
                    t = new dmapThread(serverSocket.accept(), this, componentID);
                ThreadPool.submit(t);
            }
        }catch (SocketException e) {
            System.err.println("SocketException while handling socket: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignored because we cannot handle it
                }
            }
        }
    }

    public boolean checkDomain(String domain){
        if (domain.equals(this.mailboxServer.getDomain()))
            return true;

        return false;
    }
    public boolean checkRecipient(String recipient) {
        String user = recipient.split("@")[0];

        return this.mailboxServer.checkUser(user);
    }

    public void saveMessage(Message message) {
        this.mailboxServer.save(message);
    }

    public int checkLogin(String name, String password){
        return this.mailboxServer.checkLogin(name,password);
    }

    public boolean delete(int id, String user) {
        return this.mailboxServer.delete(id, user);
    }

    public HashMap<Integer,Message> showMessages(String user) {
        HashMap<Integer, Message> msgs = this.mailboxServer.showMessages(user);
        return msgs;
    }

    public Message showMessage(int id, String user){
       return this.mailboxServer.showMessage(id,user);
    }

    public synchronized void shutdown() {
        this.listening = false;
        this.ThreadPool.shutdown();
        for (Socket sock:sockets) {
            try {
                sock.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public synchronized void addSocket(Socket socket){
        this.sockets.add(socket);
    }

    public synchronized void removeSocket(Socket socket){
        this.sockets.remove(socket);
    }

}
