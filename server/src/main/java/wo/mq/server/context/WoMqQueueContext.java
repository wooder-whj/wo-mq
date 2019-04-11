package wo.mq.server.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import wo.mq.protobuf.QueueProtos;

import java.net.SocketAddress;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@ConditionalOnMissingClass("org.springframework.data.redis.connection.RedisConnectionFactory")
public class WoMqQueueContext implements WoMqContext<QueueProtos.Queue, SocketAddress> {
    private final Logger logger= LoggerFactory.getLogger(WoMqQueueContext.class);
    private final Map<String, Queue<QueueProtos.Queue>> queueFactory =new ConcurrentHashMap<>();
    private final Map<SocketAddress,String> registrations=new ConcurrentHashMap<>();

    @Override
    public void publish(QueueProtos.Queue key) {
        String queueName = key.getHeader().getQueueName();
        Queue<QueueProtos.Queue> queue = new ConcurrentLinkedQueue<>();
        queue.offer(key);
        if(queueFactory.containsKey(queueName)){
            queueFactory.replace(queueName, queue);
        }else {
            queueFactory.put(queueName,queue);
        }
    }

    @Override
    public void put(QueueProtos.Queue key) {
        String queueName = key.getHeader().getQueueName();
        if(queueFactory.containsKey(queueName)){
            queueFactory.get(queueName).offer(key);
        }
    }

    @Override
    public QueueProtos.Queue get(QueueProtos.Queue que,SocketAddress address) {
        if(!checkRegistration(que,address)){
            return QueueProtos.Queue.getDefaultInstance();
        }
        String queueName = que.getHeader().getQueueName();
        Queue queue = queueFactory.get(queueName);
        QueueProtos.Queue.Builder queueProtobufBuilder=QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder().setQueueName(queueName)
                        .setAction(QueueProtos.QueueHeader.QueueAction.GET)
                        .setSource(address.toString()).build());
        while(true){
            QueueProtos.Queue poll = (QueueProtos.Queue)queue.poll();
            if(poll==null){
                break;
            }
            queueProtobufBuilder.addAllContent(poll.getContentList()).build();
        }
        return queueProtobufBuilder.build();
    }

    @Override
    public QueueProtos.Queue getAll(SocketAddress address) {
        QueueProtos.Queue.Builder queueProtobufBuilder=QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder().setQueueName("")
                        .setAction(QueueProtos.QueueHeader.QueueAction.GET)
                        .setSource(address.toString()).build());
        for (Map.Entry<SocketAddress, String> entry : registrations.entrySet()) {
            if (address.equals(entry.getKey())) {
                Queue<QueueProtos.Queue> queues = queueFactory.get(entry.getValue());
                while (true){
                    QueueProtos.Queue poll = queues.poll();
                    if(poll==null){
                        break;
                    }
                    queueProtobufBuilder.addAllContent(poll.getContentList());
                }
            }
        }
        return queueProtobufBuilder.build();
    }

    @Override
    public boolean checkRegistration(QueueProtos.Queue key,SocketAddress address) {
        for(Map.Entry entry:registrations.entrySet()){
            if(entry.getKey().equals(address)){
                return true;
            }
        }
        return false;
    }

    @Override
    public void subscribe(QueueProtos.Queue queue, SocketAddress address) {
        if(address==null){
            return;
        }
        registrations.put(address, queue.getHeader().getQueueName());
    }

    @Override
    public void unsubscribe(QueueProtos.Queue queue, SocketAddress address) {
        if(address==null){
            return;
        }
        registrations.remove(address, queue.getHeader().getQueueName());
    }
}
