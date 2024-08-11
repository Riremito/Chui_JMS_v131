package handling.netty;

import constants.ServerConstants;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class ServerConnection {

    private final int port;
    private int channels = -1;
    private ServerBootstrap boot;
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1); //The initial connection thread where all the new connections go to
//    private EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());
    private final EventLoopGroup workerGroup = new NioEventLoopGroup(); //Once the connection thread has finished it will be moved over to this group where the thread will be managed
    private Channel channel;

    public ServerConnection(int port, int channels) {
        this.port = port;
        this.channels = channels;
    }

    public void run() {
        try {
            boot = new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, ServerConstants.MAXIMUM_CONNECTIONS) // 指定此套接口排隊的最大連接個數
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ServerInitializer(channels));
            channel = boot.bind(port).sync().channel().closeFuture().channel();
            System.out.printf("スタート中 端口: %s\r\n", port);
        } catch (Exception e) {
            throw new RuntimeException("スタート失敗 - " + ":" + channel.remoteAddress());
        }
    }

    public void close() {
        channel.close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
