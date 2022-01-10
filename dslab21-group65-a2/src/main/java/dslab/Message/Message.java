package dslab.Message;

import java.util.LinkedList;
import java.util.List;

public class Message {

    private String subject;
    private String data;
    private String sender;
    private String hash;
    private List<String> recipients;

    public Message() {
        this.recipients = new LinkedList<>();
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHash() {
        if(hash == null)
            return "";
        else
            return hash;
    }

    public void  setHash(String hash) { this.hash = hash; }

    public String getData() { return data; }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void addRecipients(String recipient) {
        this.recipients.add(recipient);
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public List<String> getRecipients() {return this.recipients;}

    public void resetRecipients() {this.recipients = new LinkedList<>();}
}
