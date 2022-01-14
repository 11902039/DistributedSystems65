package dslab.DMTP;

import dslab.Message.Message;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class dmtpThread extends Thread {

    private Socket client;
    private IListenerThread parent;

    public dmtpThread(Socket socket, IListenerThread listenerThread){
        super("DMTPThread");
        this.client = socket;
        this.parent = listenerThread;
        this.parent.addSocket(client);
    }


    public boolean checkRecipient(String recipient){
        return this.parent.checkRecipient(recipient);
    }

    public boolean checkDomain(String domain){
        return this.parent.checkDomain(domain);
    }

    public void save(Message message){
        this.parent.saveMessage(message);
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter writer = new PrintWriter(client.getOutputStream());
        ) {
            String inputLine, outputLine;

            DMTP protocol = new DMTP(this);
            outputLine = protocol.processInput(null);
            writer.println(outputLine);
            writer.flush();

            while ((inputLine = reader.readLine()) != null) {
                outputLine = protocol.processInput(inputLine);
                writer.println(outputLine);
                writer.flush();

                if(outputLine.equalsIgnoreCase("ok bye") || outputLine.startsWith("error protocol")){
                    break; //break reading loop if user quits or if there's an error
                }
            }
            this.parent.removeSocket(client);
            client.close();
        } catch (SocketException e) {
            System.err.println("SocketException while handling socket: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
