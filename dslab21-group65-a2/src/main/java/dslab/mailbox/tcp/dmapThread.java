package dslab.mailbox.tcp;

import dslab.Message.Message;
import dslab.mailbox.DMAP.DMAP;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class dmapThread extends Thread{

    private Socket client;
    private ListenerThread parent;
    private String componentID;

    public dmapThread(Socket socket, ListenerThread listenerThread, String componentID){
        super("DMAPThread");
        this.client = socket;
        this.parent = listenerThread;
        this.parent.addSocket(client);
        this.componentID = componentID;
    }

    public int checkLogin(String name, String password){
        return this.parent.checkLogin(name,password);
    }

    public Message showMessage(int id, String user){
        return this.parent.showMessage(id, user);
    }

    public boolean delete(int id, String user){
        return this.parent.delete(id,user);
    }


    public HashMap<Integer, Message> getAllMessages(String user){
        HashMap<Integer, Message> msgs = this.parent.showMessages(user);
        return msgs;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter writer = new PrintWriter(client.getOutputStream());
        ) {
            String inputLine, outputLine;

            DMAP protocol = new DMAP(this, componentID);
            outputLine = protocol.processInput(null);
            writer.println(outputLine);
            writer.flush();

            while ((inputLine = reader.readLine()) != null) {
                outputLine = protocol.processInput(inputLine);

                if(outputLine != null) {
                    writer.println(outputLine);
                    writer.flush();


                    //if output starts with from, send 0,1,2,3,4 for other lines
                    if (protocol.DecryptString(outputLine).startsWith("from"))
                        for(int i = 0; i <= 4; i++){
                            outputLine = protocol.processInput(Integer.toString(i));
                            //outputLine = protocol.EncryptString(outputLine);
                            writer.println(outputLine);
                            writer.flush();
                        }

                    if (protocol.DecryptString(outputLine).equalsIgnoreCase("ok bye") || protocol.DecryptString(outputLine).startsWith("error protocol")) {
                        break; //break reading loop if user quits or if there's an error
                    }


                    while(outputLine!=null && (Character.isDigit(protocol.DecryptString(outputLine).charAt(0)))){ //Only list entries start with numbers
                        outputLine = protocol.processInput(null); //let protocol list all mails
                        if(outputLine!=null) { //output is null when all lines are listed.
                            writer.println(outputLine);
                            writer.flush();
                        }
                    }
                }
            }
            this.parent.removeSocket(client);
            client.close();
        } catch (SocketException e) {
            System.out.println("SocketException while handling socket: " + e.getMessage());
            return;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
    }


}
