package handling.netty;

import client.MapleClient;
import handling.RecvPacketOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import tools.MapleAESOFB;
import java.util.List;
import tools.data.ByteArrayByteStream;
import tools.data.LittleEndianAccessor;

public class MaplePacketDecoder extends ByteToMessageDecoder {

    public static class DecoderState {

        public int packetlength = -1;
    }

    public static final AttributeKey<DecoderState> DECODER_STATE_KEY = AttributeKey.newInstance(MaplePacketDecoder.class.getName() + ".STATE");

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> message) throws Exception {
        final MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        final DecoderState decoderState = ctx.channel().attr(DECODER_STATE_KEY).get();
        if (client != null && decoderState != null) {
            if (in.readableBytes() >= 4 && decoderState.packetlength == -1) {
                int packetHeader = in.readInt();
                if (!client.getReceiveCrypto().checkPacket(packetHeader)) {
//                    ctx.channel().disconnect();
                    return;
                }
                decoderState.packetlength = MapleAESOFB.getPacketLength(packetHeader);
            } else if (in.readableBytes() < 4 && decoderState.packetlength == -1) {
//                ctx.channel().disconnect();
                return;
            }
            if (in.readableBytes() >= decoderState.packetlength) {
                byte decryptedPacket[] = new byte[decoderState.packetlength];
                in.readBytes(decryptedPacket);
                decoderState.packetlength = -1;
                client.getReceiveCrypto().updateIv();
                message.add(decryptedPacket);
            }
/*        } else {
            ctx.channel().disconnect();*/
        }
    }

/*    private String lookupSend(int val) {
        for (RecvPacketOpcode op : RecvPacketOpcode.values()) {
            if (op.getValue() == val) {
                return op.name();
            }
        }
        return "UNKNOWN";
    }

    private int readFirstShort(byte[] arr) {
        return new LittleEndianAccessor(new ByteArrayByteStream(arr)).readShort();
    }*/
}