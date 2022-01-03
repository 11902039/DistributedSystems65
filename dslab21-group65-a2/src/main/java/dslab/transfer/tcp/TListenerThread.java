package dslab.transfer.tcp;

import dslab.DMTP.IListenerThread;
import dslab.DMTP.dmtpThread;
import dslab.Message.Message;
import dslab.transfer.TransferServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TListenerThread extends Thread implements IListenerThread {

    private ServerSocket serverSocket;
    private boolean listening;
    private TransferServer transferServer;
    private ExecutorService ThreadPool;


    private ConcurrentLinkedQueue<Socket> sockets = new ConcurrentLinkedQueue<>();

    public TListenerThread(ServerSocket serverSocket, TransferServer transferServer) {
        this.serverSocket = serverSocket;
        listening = true;
        this.transferServer = transferServer;
    }

    @Override
    public void run() {
        ThreadPool = Executors.newCachedThreadPool();
        try {
            while(listening) {
                Thread t = new dmtpThread(serverSocket.accept(), this);
                ThreadPool.submit(t);
            }
        } catch (SocketException e) {
            System.out.println("SocketException while handling socket: " + e.getMessage());
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

    public boolean checkRecipient(String recipient) {
        return true;
    }

    @Override
    public boolean checkDomain(String domain) {
        return true;
    }

    public void saveMessage(Message message) {
        this.transferServer.save(message);
    }

    public synchronized void shutdown(){
        listening = false;
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
