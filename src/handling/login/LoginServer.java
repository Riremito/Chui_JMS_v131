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
package handling.login;

import constants.GameConstants;
import constants.ServerConstants;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import handling.MapleServerHandler;
import java.util.HashSet;
import handling.netty.ServerConnection;
import server.ServerProperties;
import tools.Pair;

public class LoginServer {

    private static ServerConnection init;
    private static final int port = 8484;
    private static InetSocketAddress InetSocketadd;
    private static Map<Integer, Integer> load = new HashMap<Integer, Integer>();
    private static String serverName, eventMessage;
    private static byte flag;
    private static int maxCharacters, userLimit, mainserverid, usersOn = 0;
    private static boolean finishedShutdown = true, adminOnly = false;
    private static HashMap<Integer, Pair<String, String>> loginAuth = new HashMap<Integer, Pair<String, String>>();
    private static HashSet<String> loginIPAuth = new HashSet<String>();

    public static void putLoginAuth(int chrid, String ip, String tempIP) {
        loginAuth.put(chrid, new Pair<String, String>(ip, tempIP));
        loginIPAuth.add(ip);
    }

    public static Pair<String, String> getLoginAuth(int chrid) {
        return loginAuth.remove(chrid);
    }

    public static boolean containsIPAuth(String ip) {
        return loginIPAuth.contains(ip);
    }

    public static void removeIPAuth(String ip) {
        loginIPAuth.remove(ip);
    }

    public static void addIPAuth(String ip) {
        loginIPAuth.add(ip);
    }

    public static final void addChannel(final int channel) {
        load.put(channel, 0);
    }

    public static final void removeChannel(final int channel) {
        load.remove(channel);
    }

    public static final void run_startup_configurations() {
        userLimit = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.login.userlimit"));
        mainserverid = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.login.mainserverid"));
        serverName = ServerProperties.getProperty("net.sf.odinms.login.serverName");
        eventMessage = ServerProperties.getProperty("net.sf.odinms.login.eventMessage");
        flag = Byte.parseByte(ServerProperties.getProperty("net.sf.odinms.login.flag"));
        adminOnly = Boolean.parseBoolean(ServerProperties.getProperty("net.sf.odinms.world.admin", "false"));
        maxCharacters = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.login.maxCharacters"));
        try {
            init = new ServerConnection(port, -1);
            init.run();
            System.out.println("登入服務器端口: " + port);
        } catch (Exception e) {
            throw new RuntimeException("登入服務器綁定端口: " + port + " 失敗", e);
        }
    }

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("Shutting down login...");
        init.close();
        finishedShutdown = true; //nothing. lol
    }

    public static final String getServerName() {
        return serverName;
    }

/*    public static final String getTrueServerName() {
        return serverName.substring(0, serverName.length() - (GameConstants.GMS ? 2 : 3));
    }*/

    public static final String getEventMessage() {
        return eventMessage;
    }

    public static final byte getFlag() {
        return flag;
    }

    public static final int getMaxCharacters() {
        return maxCharacters;
    }

    public static final Map<Integer, Integer> getLoad() {
        return load;
    }

    public static void setLoad(final Map<Integer, Integer> load_, final int usersOn_) {
        load = load_;
        usersOn = usersOn_;
    }

    public static final void setEventMessage(final String newMessage) {
        eventMessage = newMessage;
    }

    public static final void setFlag(final byte newflag) {
        flag = newflag;
    }

    public static final int getUserLimit() {
        return userLimit;
    }

    public static final int getMainServerId() {
        return mainserverid;
    }

    public static final int getUsersOn() {
        return usersOn;
    }

    public static final void setUserLimit(final int newLimit) {
        userLimit = newLimit;
    }

    public static final boolean isAdminOnly() {
        return adminOnly;
    }

    public static final boolean isShutdown() {
        return finishedShutdown;
    }

    public static final void setOn() {
        finishedShutdown = false;
    }

    public static final void setOff() {
        finishedShutdown = true;
    }
}
