package handling.netty;

import client.MapleClient;
import constants.ServerConstants;
import handling.SendPacketOpcode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import tools.MapleAESOFB;
import java.util.concurrent.locks.Lock;
import tools.HexTool;
import tools.StringUtil;
import tools.data.ByteArrayByteStream;
import tools.data.LittleEndianAccessor;

public class MaplePacketEncoder extends MessageToByteEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object message, ByteBuf buffer) throws Exception {
        final MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client != null) {
            MapleAESOFB send_crypto = client.getSendCrypto();
            byte[] input = ((byte[]) message);
            if (ServerConstants.ShowSendP) {
                final int pHeader = ((input[0]) & 0xFF) + (((input[1]) & 0xFF) << 8);
//                String op = SendPacketOpcode.nameOf(pHeader);
                String pHeaderStr = Integer.toHexString(pHeader).toUpperCase();
                pHeaderStr = "0x" + StringUtil.getLeftPaddedStr(pHeaderStr, '0', 4);
                final StringBuilder sb = new StringBuilder("[發送] " + pHeaderStr);
                sb.append("\r\n\r\n").append(HexTool.toString((byte[]) message)).append("\r\n").append(HexTool.toStringFromAscii((byte[]) message));
                System.out.println(sb.toString());
//                sb.append("\r\n\r\n").append(HexTool.toString((byte[]) message)).append("\r\n").append(HexTool.toStringFromAscii((byte[]) message));
            }
            final byte[] unencrypted = new byte[input.length];
            System.arraycopy(input, 0, unencrypted, 0, input.length); // Copy the input > "unencrypted"
            final byte[] ret = new byte[unencrypted.length + 4]; // Create new bytes with length = "unencrypted" + 4
            final Lock mutex = client.getLock();
            mutex.lock();
            try {
                final byte[] header = send_crypto.getPacketHeader(unencrypted.length);
                send_crypto.updateIv(); // Crypt it with IV
                System.arraycopy(header, 0, ret, 0, 4); // Copy the header > "Ret", first 4 bytes
                System.arraycopy(unencrypted, 0, ret, 4, unencrypted.length); // Copy the unencrypted > "ret"
                buffer.writeBytes(ret);
            } finally {
                mutex.unlock();
            }
        } else { // no client object created yet, send unencrypted (hello) 這裡是發送 gethello 封包 無需加密
            buffer.writeBytes((byte[]) message);
        }
    }

/*    private String lookupRecv(int val) {
        for (SendPacketOpcode op : SendPacketOpcode.values()) {
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
