package handling.netty;

import handling.MapleServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final int channels;

    public ServerInitializer(int channels) {
        this.channels = channels;
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline pipe = channel.pipeline();
        // UserEventTriggered
//        pipe.addLast("idleStateHandler", new IdleStateHandler(20, 0, 0)); // 第一個數字是幾秒對方沒傳來封包 第二個是你幾秒沒傳封包給對方 第三個是綜合 之後會觸發這個userEventTriggered
        pipe.addLast("decoder", new MaplePacketDecoder()); // decodes the packet
        pipe.addLast("encoder", new MaplePacketEncoder()); //encodes the packet
        pipe.addLast("handler", new MapleServerHandler(channels));
    }
}
