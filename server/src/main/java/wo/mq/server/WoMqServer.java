package wo.mq.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;
import wo.mq.protobuf.QueueProtos;
import wo.mq.server.configuration.WoMqServerProperties;
import wo.mq.server.context.WoMqContext;

import java.util.List;

import static wo.mq.protobuf.QueueProtos.QueueHeader.QueueAction.*;
import static wo.mq.protobuf.QueueProtos.QueueHeader.QueueAction.GET;

@Component
public class WoMqServer implements ApplicationRunner {
    final AnnotationConfigApplicationContext applicationContext;
    final WoMqServerProperties properties;

    @Autowired
    public WoMqServer(AnnotationConfigApplicationContext applicationContext, WoMqServerProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();
        EventLoopGroup executors = new NioEventLoopGroup();
        try{
            List<String> ports = args.getOptionValues("port");
            Integer port=ports==null?properties.getPort()==null?8880:properties.getPort():Integer.valueOf(ports.get(0));
            ChannelFuture channelFuture = new ServerBootstrap().group(boss, worker).channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO)).option(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                            pipeline.addLast("protobufDecoder", new ProtobufDecoder(QueueProtos.Queue.getDefaultInstance()));
                            pipeline.addLast("lengthFieldPrepender", new LengthFieldPrepender(4));
                            pipeline.addLast("protobufEncoder", new ProtobufEncoder());
                            pipeline.addLast(executors,"woMqServerChannelHandler",new QueueWoMqServerChannelHandler());
                        }
                    }).bind(port).sync();
            channelFuture.channel().closeFuture().sync();
        }finally {
            executors.shutdownGracefully();
            worker.shutdownGracefully();
            boss.shutdownGracefully();
        }
    }

    private class QueueWoMqServerChannelHandler extends ChannelInboundHandlerAdapter {
        private final Logger logger= LoggerFactory.getLogger(WoMqServerChannelHandler.class);
        private final ChannelGroup channelGroup=new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        private WoMqContext woMqContext=applicationContext.getBean(WoMqContext.class);

        @SuppressWarnings("unchecked")
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(woMqContext.getAll(ctx.channel().remoteAddress()));
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            channelGroup.add(ctx.channel());
        }

        @SuppressWarnings("unchecked")
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if(msg==null){
                return;
            }
            try{
                QueueProtos.Queue message=(QueueProtos.Queue)msg;
                QueueProtos.QueueHeader.QueueAction action = message.getHeader().getAction();
                if(action.equals(PUBLISH)){
                    woMqContext.publish(message);
                }else if(action.equals(SUBSCRIPT)){
                    woMqContext.subscribe(message,ctx.channel().remoteAddress());
                }else if(action.equals(UNSUBSCRIPT)){
                    woMqContext.unsubscribe(message,ctx.channel().remoteAddress());
                }else if(action.equals(PUT)){
                    woMqContext.put(message);
                }else if(action.equals(GET)){
                    for (Channel channel:channelGroup){
                        channel.writeAndFlush(woMqContext.get(message, channel.remoteAddress()));
                    }
                }
            }finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.close();
            logger.error(cause.getMessage(),cause);
        }
    }
}
