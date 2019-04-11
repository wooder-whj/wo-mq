package wo.mq.client;

import java.util.List;

public interface MqClient {
    boolean publish(String queueName, String content,long ... timeout) throws InterruptedException;
    boolean subscribe(String queueName, long ... timeout) throws InterruptedException;
    boolean unsubscribe(String queueName, long ... timeout) throws InterruptedException;
    boolean put(String queueName, String content,long ... timeout) throws InterruptedException;
    List<String> get(String queueName,long ... timeout) throws InterruptedException;
    void open(String host, Integer port);
    boolean isOpen();
    void close();
}
