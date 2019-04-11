package wo.mq.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wo.mq.protobuf.QueueProtos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WoMqClient implements MqClient {
    private static final Logger logger= LoggerFactory.getLogger(WoMqClient.class);
    private static volatile WoMqClient producer=null;
    private Channel channel=null;
    private volatile List<String> messages=null;
    private final Lock lock=new ReentrantLock();
    private final Condition condition=lock.newCondition();
    private final Condition con=lock.newCondition();
    private final String host;
    private final Integer port;

    private WoMqClient(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    private void init(){
        EventLoopGroup worker=new NioEventLoopGroup();
        worker.submit(()->{
            NioEventLoopGroup group = new NioEventLoopGroup();
            try{
                channel = new Bootstrap().group(group).channel(NioSocketChannel.class)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                                pipeline.addLast("protobufDecoder", new ProtobufDecoder(QueueProtos.Queue.getDefaultInstance()));
                                pipeline.addLast("lengthFieldPrepender", new LengthFieldPrepender(4));
                                pipeline.addLast("protobufEncoder", new ProtobufEncoder());
                                pipeline.addLast(worker,"woMqProducerChannelHandler", new WoMqClientChannelHandler());
                            }
                        }).connect(host, port).sync().channel();
                lock.lock();
                try {
                    if(channel!=null){
                        con.signal();
                    }
                }finally {
                    lock.unlock();
                }
                channel.closeFuture().sync();
            } catch (InterruptedException e) {
                logger.error(e.getMessage(),e );
                if(channel!=null){
                    channel.close();
                }
            } finally {
                group.shutdownGracefully();
            }
        });
        lock.lock();
        try {
            if(channel==null){
                con.await(1000,TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(),e );
        } finally {
            lock.unlock();
        }
    }

    public static WoMqClient getInstance(String host, Integer port){
        if(producer!=null){
            return producer;
        }
        synchronized (WoMqClient.class){
            if(producer!=null){
                return producer;
            }
            producer=new WoMqClient(host, port);
            return producer;
        }
    }

    @Override
    public boolean publish(String queueName, String content,long ... timeout) throws InterruptedException {
        if(!isOpen()){
            init();
        }
        final boolean[] result = {false};
        QueueProtos.Queue queue = QueueProtos.Queue.newBuilder().setHeader(QueueProtos.QueueHeader.newBuilder().setQueueName(queueName)
                .setAction(QueueProtos.QueueHeader.QueueAction.PUBLISH).setSource(channel.localAddress().toString()).build())
                .addContent(content).build();
        channel.writeAndFlush(queue).addListener((ChannelFutureListener) future -> {
            if(future.isSuccess()){
                result[0] =true;
                return;
            }
        }).await(timeout.length==0?5000:timeout[0], TimeUnit.MILLISECONDS );
        return result[0];
    }

    @Override
    public boolean subscribe(String queueName, long ... timeout) throws InterruptedException {
        if(!isOpen()){
            init();
        }
        final boolean[]result={false};
        QueueProtos.Queue queue = QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder()
                        .setAction(QueueProtos.QueueHeader.QueueAction.SUBSCRIPT)
                        .setQueueName(queueName)
                        .setSource(channel.localAddress().toString()).build())
                .build();
        channel.writeAndFlush(queue).addListener((ChannelFutureListener)future->{
            if(future.isSuccess()){
                result[0]=true;
                return;
            }
        }).await(timeout.length==0?5000:timeout[0], TimeUnit.MILLISECONDS);
        return result[0];
    }

    @Override
    public boolean unsubscribe(String queueName, long... timeout) throws InterruptedException {
        if(!isOpen()){
            init();
        }
        final boolean[]result={false};
        QueueProtos.Queue queue = QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder()
                        .setAction(QueueProtos.QueueHeader.QueueAction.UNSUBSCRIPT)
                        .setQueueName(queueName)
                        .setSource(channel.localAddress().toString()).build())
                .build();
        channel.writeAndFlush(queue).addListener((ChannelFutureListener)future->{
            if(future.isSuccess()){
                result[0]=true;
                return;
            }
        }).await(timeout.length==0?5000:timeout[0], TimeUnit.MILLISECONDS);
        return result[0];
    }

    @Override
    public boolean put(String queueName, String content,long ... timeout) throws InterruptedException {
        if(!isOpen()){
            init();
        }
        final boolean[]result={false};
        QueueProtos.Queue queue = QueueProtos.Queue.newBuilder()
                .setHeader(QueueProtos.QueueHeader.newBuilder()
                        .setAction(QueueProtos.QueueHeader.QueueAction.PUT)
                        .setQueueName(queueName)
                        .setSource(channel.localAddress().toString()).build())
                .addContent(content).build();
        channel.writeAndFlush(queue).addListener((ChannelFutureListener) future -> {
            if(future.isSuccess()){
                result[0]=true;
                return;
            }
        }).await(timeout.length==0?5000:timeout[0], TimeUnit.MILLISECONDS);
        return result[0];
    }

    @Override
    public List<String> get(String queueName,long ... timeout) throws InterruptedException {
        if(!isOpen()){
            init();
        }
        lock.lock();
        try {
            messages=null;
            QueueProtos.Queue queue = QueueProtos.Queue.newBuilder()
                    .setHeader(QueueProtos.QueueHeader.newBuilder()
                            .setAction(QueueProtos.QueueHeader.QueueAction.GET)
                            .setQueueName(queueName)
                            .setSource(channel.localAddress().toString()).build())
                    .build();
            channel.writeAndFlush(queue);
            condition.await(timeout.length==0?5000:timeout[0], TimeUnit.MILLISECONDS);
        }finally {
            lock.unlock();
        }
        return messages;
    }

    @Override
    public void open(String host, Integer port) {
        if(this.channel==null){
            init();
        }else if(!this.channel.isOpen()){
            this.channel.close();
            init();
        }
    }

    @Override
    public boolean isOpen() {
        return this.channel!=null&&this.channel.isOpen();
    }

    @Override
    public void close() {
        if(this.channel!=null){
            this.channel.close();
        }
    }

    private class WoMqClientChannelHandler extends ChannelDuplexHandler {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if(msg==null){
                return;
            }
            lock.lock();
            try {
                QueueProtos.Queue message=(QueueProtos.Queue)msg;
                messages=new ArrayList<>();
                message.getContentList().asByteStringList().forEach(content->{
                    messages.add(content.toStringUtf8());
                });
                condition.signal();
            }finally {
                lock.unlock();
            }
        }
    }
}
