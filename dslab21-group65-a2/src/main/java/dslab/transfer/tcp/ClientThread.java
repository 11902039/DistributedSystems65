package dslab.transfer.tcp;

import dslab.Message.Message;
import dslab.transfer.TransferServer;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

public class ClientThread extends Thread{

    private Message message;
    private TransferServer server;
    private Socket client;

    public ClientThread(Message message, TransferServer server, Socket socket){
        this.message = message;
        this.server = server;
        this.client = socket;
    }

    @Override
    public void run() {
        try {

            BufferedReader serverReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter serverWriter = new PrintWriter(client.getOutputStream());

            String inputLine = serverReader.readLine();
            if(inputLine != null && inputLine.equals("ok DMTP2.0")){
                    serverWriter.println("begin");
                    serverWriter.flush();

                    if (!serverReader.readLine().startsWith("ok"))
                        serverReader.readLine();

                    serverWriter.println("from " + message.getSender());
                    serverWriter.flush();

                    serverReader.readLine();

                    serverWriter.println("subject " + message.getSubject());
                    serverWriter.flush();

                    serverReader.readLine();

                    serverWriter.println("data " + message.getData());
                    serverWriter.flush();

                    serverReader.readLine();

                    if(!message.getHash().equals("")) {
                        serverWriter.println("hash " + message.getHash());
                        serverWriter.flush();

                        serverReader.readLine();
                    }
                    String outputline = "to ";
                    List<String> recips = message.getRecipients();
                    outputline += recips.get(0);
                    for (int i = 1; i < recips.size(); i++){
                        outputline += "," + recips.get(i);
                    }
                    serverWriter.println(outputline);
                    serverWriter.flush();

                    inputLine = serverReader.readLine();

                    if(inputLine.startsWith("error")){
                        String[] parts = inputLine.split("\\s");
                        if(parts[1].equals("unknown") && parts[2].equals("recipient")){
                            String errorUser = parts[3];
                            String recipients = "";
                            String data = "";
                            if(recips.size()>1){
                                recipients += " The other recipients were: ";
                            }
                            for(String rec:recips){
                                if (rec.startsWith(errorUser)){
                                    data = "Dear User, the message you sent could not be delivered since at least the recipient " +
                                            rec + " does not exist. We apologize for the inconvenience. The content of the " +
                                            "message was: \"" + message.getData() + "\"";
                                }
                                else
                                    recipients += rec + " ";
                            }
                            this.server.errorMessager(message.getSender(),data + recipients,message.getSubject());
                        }
                    }
                serverWriter.println("send");
                serverWriter.flush();
                if (serverReader.readLine().startsWith("ok")) {
                    int a = this.client.getPort();
                    String b = this.client.getLocalAddress().getHostAddress();
                    String server = b + ":" + a;
                    String user  = this.message.getSender();
                    this.server.sendPacket(server + " " + user);
                }

                serverWriter.println("quit");
                serverWriter.flush();
            }
        } catch (UnknownHostException e) {
            System.err.println("Cannot connect to host: " + e.getMessage());
        } catch (SocketException e) {
            System.err.println("SocketException while handling socket: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (client != null && !client.isClosed()) {
                try {
                    client.close();
                } catch (IOException e) {
                    // Ignored because we cannot handle it
                }
            }
        }
    }
}
