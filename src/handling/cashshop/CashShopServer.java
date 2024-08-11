/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package handling.cashshop;

import constants.ServerConstants;
import java.net.InetSocketAddress;

import handling.MapleServerHandler;
import handling.channel.PlayerStorage;
import handling.netty.ServerConnection;
import server.MTSStorage;
import server.ServerProperties;

public class CashShopServer {

    private static ServerConnection init;
    private static String ip;
    private static InetSocketAddress InetSocketadd;
    private static final short DEFAULT_PORT = 8487;
    private static final short port = Short.parseShort(ServerProperties.getProperty("net.sf.odinms.cashshop.net.port", String.valueOf(DEFAULT_PORT)));;
//    private static short port = 8487/*8600*/;
    private static PlayerStorage players/*, playersMTS*/;
    private static boolean finishedShutdown = false;

    public static final void run_startup_configurations() {
//        port = Short.parseShort(ServerProperties.getProperty("net.sf.odinms.cashshop.net.port", String.valueOf(8487)));
        ip = ServerConstants.IP/*ServerProperties.getProperty("net.sf.odinms.world.host")*/ + ":" + port;
        players = new PlayerStorage(-10);
        try {
            init = new ServerConnection(port, -10);
            init.run();
        } catch (final Exception e) {
            throw new RuntimeException("商城服務器綁定端口 " + port + " 失敗", e);
        }
    }

    public static final String getIP() {
        return ip;
    }

    public static final PlayerStorage getPlayerStorage() {
        return players;
    }

/*    public static final PlayerStorage getPlayerStorageMTS() {
        return playersMTS;
    }*/

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("Saving all connected clients (CS)...");
        players.disconnectAll();
//	playersMTS.disconnectAll();
        MTSStorage.getInstance().saveBuyNow(true);
        System.out.println("Shutting down CS...");
        init.close();
        finishedShutdown = true;
    }

    public static boolean isShutdown() {
	return finishedShutdown;
    }
}
