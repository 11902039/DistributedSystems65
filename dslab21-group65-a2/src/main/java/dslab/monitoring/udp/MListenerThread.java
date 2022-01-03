package dslab.monitoring.udp;

import dslab.monitoring.MonitoringServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class MListenerThread extends Thread{

    private DatagramSocket datagramSocket;
    private boolean listening = true;
    private MonitoringServer server;

    public MListenerThread(DatagramSocket dgSocket, MonitoringServer server){
        this.datagramSocket = dgSocket;
        this.server = server;
    }

    @Override
    public void run(){
        byte[] buffer;
        DatagramPacket packet;
        try {
            while (listening) {

                buffer = new byte[1024];

                packet = new DatagramPacket(buffer, buffer.length);

                // wait for incoming packets from client
                datagramSocket.receive(packet);
                // get the data from the packet
                String request = new String(packet.getData(),0,packet.getLength());

                String[] parts = request.split("\\s");

                if(parts.length == 2) {
                    String server = parts[0];
                    String user = parts[1];

                    this.server.addMail(server, user);
                }
            }
        } catch (SocketException e) {
            System.out.println("SocketException while waiting for/handling packets: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (datagramSocket != null && !datagramSocket.isClosed()) {
                datagramSocket.close();
            }
        }
    }

    public void shutdown(){
        listening = false;
    }
}
