package wo.mq.server.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import wo.mq.protobuf.QueueProtos;

import java.net.SocketAddress;

@SuppressWarnings("unchecked")
@Component
@ConditionalOnClass(RedisConnectionFactory.class)
public class WoMqRedisContext implements WoMqContext<QueueProtos.Queue, SocketAddress> {
    private final Logger logger= LoggerFactory.getLogger(WoMqRedisContext.class);

    private final RedisTemplate redisTemplate;

    @Autowired
    public WoMqRedisContext(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(QueueProtos.Queue msg) {
        String queueName = msg.getHeader().getQueueName();
        redisTemplate.opsForList().rightPush(queueName,msg );
    }

    @Override
    public void put(QueueProtos.Queue msg) {
        String queueName = msg.getHeader().getQueueName();
        redisTemplate.opsForList().rightPushIfPresent(queueName, msg);
    }

    @Override
    public QueueProtos.Queue get(QueueProtos.Queue que,SocketAddress address) {
        if(!checkRegistration(que,address)){
            return QueueProtos.Queue.getDefaultInstance();
        }
        String queueName = que.getHeader().getQueueName();
        QueueProtos.Queue.Builder queueProtobufBuilder=QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder().setQueueName(queueName)
                        .setAction(QueueProtos.QueueHeader.QueueAction.GET)
                        .setSource(address.toString()).build());
        redisTemplate.opsForSet().members(address).forEach(queueKey->{
            if(queueName.equals(queueKey)){
                while (true){
                    QueueProtos.Queue pop =(QueueProtos.Queue) redisTemplate.opsForList().leftPop(queueName);
                    if(pop==null){
                        break;
                    }
                    queueProtobufBuilder.addAllContent(pop.getContentList()).build();
                }
            }
        });
        return queueProtobufBuilder.build();
    }

    @Override
    public QueueProtos.Queue getAll(SocketAddress address) {
        QueueProtos.Queue.Builder queueProtobufBuilder=QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder().setQueueName("")
                        .setAction(QueueProtos.QueueHeader.QueueAction.GET)
                        .setSource(address.toString()).build());

        redisTemplate.opsForSet().members(address).forEach(queueKey->{
            while (true){
                QueueProtos.Queue pop =(QueueProtos.Queue) redisTemplate.opsForList().leftPop(queueKey);
                if(pop==null){
                    break;
                }
                queueProtobufBuilder.addAllContent(pop.getContentList()).build();
            }
        });
        return queueProtobufBuilder.build();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean checkRegistration(QueueProtos.Queue msg,SocketAddress address) {
        return redisTemplate.opsForSet().isMember(address, msg.getHeader().getQueueName());
    }

    @Override
    public void subscribe(QueueProtos.Queue msg, SocketAddress address) {
        if(address==null){
            return;
        }
        redisTemplate.opsForSet().add(address, msg.getHeader().getQueueName());
    }

    @Override
    public void unsubscribe(QueueProtos.Queue msg, SocketAddress address) {
        if(address==null){
            return;
        }
        redisTemplate.opsForSet().remove(address,msg.getHeader().getQueueName());
    }
}
