package wo.mq.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wo.mq.protobuf.QueueProtos;

import java.net.SocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static wo.mq.protobuf.QueueProtos.QueueHeader.QueueAction.*;

public class WoMqServerChannelHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger= LoggerFactory.getLogger(WoMqServerChannelHandler.class);
    private final Map<String, Queue> queueFactory =new ConcurrentHashMap<>();
    private final Map<SocketAddress,String> registrations=new ConcurrentHashMap<>();
    private final ChannelGroup channelGroup=new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
            registrations.entrySet().forEach(entry->{
                if(ctx.channel().remoteAddress().equals(entry.getKey())){
                    queueFactory.get(entry.getValue()).forEach(ctx::writeAndFlush);
                }
            });
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        channelGroup.add(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg==null){
            return;
        }
        try{
            QueueProtos.Queue message=(QueueProtos.Queue)msg;
            QueueProtos.QueueHeader.QueueAction action = message.getHeader().getAction();
            if(action.equals(PUBLISH)){
                publishQueue(message);
            }else if(action.equals(SUBSCRIPT)){
                subscriptQueue(ctx.channel(),message);
            }else if(action.equals(UNSUBSCRIPT)){
                unsubscriptQueue(ctx.channel(),message);
            }else if(action.equals(PUT)){
                putQueue(message);
            }else if(action.equals(GET)){
                for (Channel channel:channelGroup){
                    getQueue(channel,message);
                }
            }
        }finally {
            printQueueFactory();
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        logger.error(cause.getMessage(),cause);
    }

    private void printQueueFactory() {
        queueFactory.entrySet().forEach(entry->{
            logger.info("Queue " + entry.getKey() +": ");
            entry.getValue().forEach(message->{
                QueueProtos.Queue msg=(QueueProtos.Queue)message;
                msg.getContentList().asByteStringList().forEach(content->{
                    logger.info(content.toStringUtf8());
                });
            });
        });
    }

    private void getQueue(Channel channel, QueueProtos.Queue message) {
        if(!checkRegistration(channel,message)){
            channel.writeAndFlush(QueueProtos.Queue.getDefaultInstance());
            return;
        }
        String queueName = message.getHeader().getQueueName();
        Queue queue = queueFactory.get(queueName);
        QueueProtos.Queue.Builder queueProtobufBuilder=QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder().setQueueName(queueName)
                        .setAction(QueueProtos.QueueHeader.QueueAction.GET)
                        .setSource(channel.localAddress().toString()).build());
        while(true){
            QueueProtos.Queue poll = (QueueProtos.Queue)queue.poll();
            if(poll==null){
                break;
            }
            queueProtobufBuilder.addAllContent(poll.getContentList()).build();
        }
        channel.writeAndFlush(queueProtobufBuilder.build());
    }

    private boolean checkRegistration(Channel channel, QueueProtos.Queue message) {
        for(Map.Entry entry:registrations.entrySet()){
            if(entry.getKey().equals(channel.remoteAddress())){
                return true;
            }
        }
        return false;
    }

    private void putQueue(QueueProtos.Queue message) {
        Queue queue = queueFactory.get(message.getHeader().getQueueName());
        if(queue==null){
            return;
        }
        queue.offer(message);
        logger.info("Put Queue "+ message.getHeader().getQueueName()
                + "-"+message.getContent(0)
                + " from " + message.getHeader().getSource() + " successfully.");
    }

    private void subscriptQueue(Channel channel, QueueProtos.Queue message) {
        SocketAddress remoteAddress = channel.remoteAddress();
        if(remoteAddress==null){
            return;
        }
        registrations.put(remoteAddress, message.getHeader().getQueueName());
        logger.info("Subscript Queue "+ message.getHeader().getQueueName()+" successfully.");
    }

    private void unsubscriptQueue(Channel channel, QueueProtos.Queue message) {
        SocketAddress remoteAddress = channel.remoteAddress();
        if(remoteAddress==null){
            return;
        }
        registrations.remove(remoteAddress, message.getHeader().getQueueName());
        logger.info("Unsubscript Queue "+ message.getHeader().getQueueName()+" successfully.");
    }

    private void publishQueue(QueueProtos.Queue message) {
        String queueName = message.getHeader().getQueueName();
        Queue<QueueProtos.Queue> queue = new ConcurrentLinkedQueue<>();
        queue.offer(message);
        if(queueFactory.containsKey(queueName)){
            queueFactory.replace(queueName, queue);
        }else {
            queueFactory.put(queueName,queue);
        }
        logger.info("Publish Queue "+queueName
                + "-"+message.getContent(0)
                +" from "
                +message.getHeader().getSource()+" successfully.");
    }
}
