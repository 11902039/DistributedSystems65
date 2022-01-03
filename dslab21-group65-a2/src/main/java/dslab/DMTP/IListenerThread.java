package dslab.DMTP;

import dslab.Message.Message;

import java.net.Socket;

public interface IListenerThread { //for generalizing dmtpThread

    boolean checkRecipient(String recipient);

    void saveMessage(Message message);

    boolean checkDomain(String domain);

    void addSocket(Socket socket);

    void removeSocket(Socket socket);
}
