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
package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;

import client.MapleQuestStatus;
import client.SkillFactory;
import constants.GameConstants;
import handling.cashshop.CashShopServer;
import handling.cashshop.handler.CashShopOperation;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.CharacterIdChannelPair;
import handling.world.CharacterTransfer;
import handling.world.MapleMessenger;
import handling.world.MapleMessengerCharacter;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.PlayerBuffStorage;
import handling.world.World;
import handling.world.exped.MapleExpedition;
import handling.world.guild.MapleGuild;
import java.util.List;
import server.maps.FieldLimitType;
import server.quest.MapleQuest;
import tools.FileoutputUtil;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.data.LittleEndianAccessor;
import tools.packet.MTSCSPacket;

public class InterServerHandler {

    public static final void EnterCS(final MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer() == null || chr.hasBlockedInventory() || chr.getMap() == null || chr.getEventInstance() != null || c.getChannelServer() == null) {
            c.announce(MaplePacketCreator.serverBlocked(2));
            c.announce(MaplePacketCreator.enableActions());
            return;
        }
        /*        if (World.getPendingCharacterSize() >= 10) {
            chr.dropMessage(1, "The server is busy at the moment. Please try again in a minute or less.");
            c.announce(MaplePacketCreator.enableActions());
            return;
        }*/
        final ChannelServer ch = ChannelServer.getInstance(c.getChannel());
        chr.changeRemoval();
        if (chr.getMessenger() != null) {
            MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(chr);
            World.Messenger.leaveMessenger(chr.getMessenger().getId(), messengerplayer);
        }
        PlayerBuffStorage.addBuffsToStorage(chr.getId(), chr.getAllBuffs());
        PlayerBuffStorage.addCooldownsToStorage(chr.getId(), chr.getCooldowns());
        PlayerBuffStorage.addDiseaseToStorage(chr.getId(), chr.getAllDiseases());
        World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), -10);
        ch.removePlayer(chr);
        c.updateLoginState(MapleClient.CHANGE_CHANNEL, c.getSessionIPAddress());
        final String s = c.getSessionIPAddress();
        LoginServer.addIPAuth(s.substring(s.indexOf('/') + 1, s.length()));
        c.announce(MaplePacketCreator.getChannelChange(c, Integer.parseInt(CashShopServer.getIP().split(":")[1])));
        chr.saveToDB(false, false);
        chr.getMap().removePlayer(chr);
        c.setPlayer(null);
        c.setReceiving(false);
    }

    public static final void Loggedin(final int playerid, final MapleClient c) {
        if (c.getPlayer() != null || World.Find.findChannel(playerid) != -1 || CashShopServer.getPlayerStorage().getPendingCharacter(playerid) != null || CashShopServer.getPlayerStorage().getCharacterById(playerid) != null) {
            c.getSession().close();
            return;
        }
        final ChannelServer channelServer = c.getChannelServer();
        MapleCharacter player;
//        CharacterTransfer transfer = null;
//        transfer = channelServer.getPlayerStorage().getPendingCharacter(playerid);
        final CharacterTransfer transfer = channelServer.getPlayerStorage().getPendingCharacter(playerid);
        if (transfer != null) { // 這裡是換頻或從商城回來
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                if (cs != channelServer) {
                    if (cs.getPlayerStorage().getPendingCharacter(playerid) != null) {
                        c.getSession().close();
                        return;
                    }
                }
                if (cs.getPlayerStorage().getCharacterById(playerid) != null) {
                    c.getSession().close();
                    return;
                }
            }
            player = MapleCharacter.ReconstructChr(transfer, c, true);
        } else { // 第一次登入 角色沒有在PlayerStorage裡面
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                if (cs.getPlayerStorage().getPendingCharacter(playerid) != null || cs.getPlayerStorage().getCharacterById(playerid) != null) {
                    c.getSession().close();
                    return;
                }
            }
            Pair<String, String> ip = LoginServer.getLoginAuth(playerid);
            String s = c.getSessionIPAddress();
            if (ip == null || !s.substring(s.indexOf('/') + 1, s.length()).equals(ip.left)) {
                if (ip != null) {
                    LoginServer.putLoginAuth(playerid, ip.left, ip.right);
                }
                c.getSession().close();
                return;
            }
            c.setTempIP(ip.right);
            player = MapleCharacter.loadCharFromDB(playerid, c, true);
            player.getStat().recalcLocalStats(true, player);
        }
        /*        if (transfer == null) { // 第一次登入 角色沒有在PlayerStorage裡面
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                if (cs.getPlayerStorage().getPendingCharacter(playerid) != null) {
                    c.getSession().close();
                    return;
                }
            }
            if (CashShopServer.getPlayerStorage().getPendingCharacter(playerid) != null || CashShopServer.getPlayerStorage().getCharacterById(playerid) != null) {
                c.getSession().close();
                return;
            }
            Pair<String, String> ip = LoginServer.getLoginAuth(playerid);
            String s = c.getSessionIPAddress();
            if (ip == null || !s.substring(s.indexOf('/') + 1, s.length()).equals(ip.left)) {
                if (ip != null) {
                    LoginServer.putLoginAuth(playerid, ip.left, ip.right);
                }
                c.getSession().close();
                return;
            }
            c.setTempIP(ip.right);
            player = MapleCharacter.loadCharFromDB(playerid, c, true);
            player.getStat().recalcLocalStats(true, player);
        } else { // 這裡是換頻或從商城回來
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                if (cs != channelServer) {
                    if (cs.getPlayerStorage().getPendingCharacter(playerid) != null) {
                        c.getSession().close();
                        return;
                    }
                }
            }
            if (CashShopServer.getPlayerStorage().getPendingCharacter(playerid) != null || CashShopServer.getPlayerStorage().getCharacterById(playerid) != null) {
                c.getSession().close();
                return;
            }
            player = MapleCharacter.ReconstructChr(transfer, c, true);
        }*/

        if (player == null) {
            c.getSession().close();
            return;
        }
        c.setPlayer(player);
        c.setAccID(player.getAccountID());
        if (!c.CheckIPAddress()) { // Remote hack
            c.getSession().close();
            return;
        }
        final int state = c.getLoginState();
        boolean allowLogin = false;
        if (state == MapleClient.LOGIN_SERVER_TRANSITION || state == MapleClient.CHANGE_CHANNEL || state == MapleClient.LOGIN_NOTLOGGEDIN) {
            allowLogin = !World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()));
        }
        if (!allowLogin) {
            c.setPlayer(null);
            c.getSession().close();
            return;
        }
        c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());
        channelServer.addPlayer(player);

        player.giveCoolDowns(PlayerBuffStorage.getCooldownsFromStorage(player.getId()));
        player.silentGiveBuffs(PlayerBuffStorage.getBuffsFromStorage(player.getId()));
        player.giveSilentDebuff(PlayerBuffStorage.getDiseaseFromStorage(player.getId()));

        //c.announce(MaplePacketCreator.getCharInfo(player));
        {
            byte[] packet = MaplePacketCreator.getCharInfo(player);
            c.announce(packet);
            short header = (short) (((short) packet[0] & 0xFF) | ((short) packet[1] & 0xFF << 8));
            String text = String.format("@%04X", header);

            for (int i = 2; i < packet.length; i++) {
                text += String.format(" %02X", packet[i]);
            }

            System.out.println("[SP] " + text);
        }

        c.announce(MTSCSPacket.enableCSUse());
        /*        if (player.isGM()) {
            SkillFactory.getSkill(5001004).getEffect(1).applyTo(player);
        }*/
//        c.announce(MaplePacketCreator.temporaryStats_Reset()); // .
        player.getMap().addPlayer(player);
        try {
            // Start of buddylist
            final int buddyIds[] = player.getBuddylist().getBuddyIds();
            World.Buddy.loggedOn(player.getName(), player.getId(), c.getChannel(), buddyIds);
            if (player.getParty() != null) {
                final MapleParty party = player.getParty();
                World.Party.updateParty(party.getId(), PartyOperation.LOG_ONOFF, new MaplePartyCharacter(player));
                /*                if (party != null && party.getExpeditionId() > 0) {
                    final MapleExpedition me = World.Party.getExped(party.getExpeditionId());
                    if (me != null) {
                        c.announce(MaplePacketCreator.expeditionStatus(me, false));
                    }
                }*/
            }
            /*            if (player.getSidekick() == null) {
                player.setSidekick(World.Sidekick.getSidekickByChr(player.getId()));
            }
            if (player.getSidekick() != null) {
                c.announce(MaplePacketCreator.updateSidekick(player, player.getSidekick(), false));
            }*/
            final CharacterIdChannelPair[] onlineBuddies = World.Find.multiBuddyFind(player.getId(), buddyIds);
            for (CharacterIdChannelPair onlineBuddy : onlineBuddies) {
                player.getBuddylist().get(onlineBuddy.getCharacterId()).setChannel(onlineBuddy.getChannel());
            }
            c.announce(MaplePacketCreator.updateBuddylist(player.getBuddylist().getBuddies()));
            // Start of Messenger
            final MapleMessenger messenger = player.getMessenger();
            if (messenger != null) {
                World.Messenger.silentJoinMessenger(messenger.getId(), new MapleMessengerCharacter(c.getPlayer()));
                World.Messenger.updateMessenger(messenger.getId(), c.getPlayer().getName(), c.getChannel());
            }
            // Start of Guild and alliance
            if (player.getGuildId() > 0) {
                World.Guild.setGuildMemberOnline(player.getMGC(), true, c.getChannel());
                c.announce(MaplePacketCreator.showGuildInfo(player));
                final MapleGuild gs = World.Guild.getGuild(player.getGuildId());
                if (gs == null) {
                    player.setGuildId(0);
                    player.setGuildRank((byte) 5);
                    player.setAllianceRank((byte) 5);
                    player.saveGuildStatus();
                }
                /*                if (gs != null) {
                    final List<byte[]> packetList = World.Alliance.getAllianceInfo(gs.getAllianceId(), true);
                    if (packetList != null) {
                        for (byte[] pack : packetList) {
                            if (pack != null) {
                                c.announce(pack);
                            }
                        }
                    }
                } else { //guild not found, change guild id
                    player.setGuildId(0);
                    player.setGuildRank((byte) 5);
                    player.setAllianceRank((byte) 5);
                    player.saveGuildStatus();
                }*/
            }
            /*            if (player.getFamilyId() > 0) {
                World.Family.setFamilyMemberOnline(player.getMFC(), true, c.getChannel());
            }*/
        } catch (Exception e) {
            FileoutputUtil.outputFileError(FileoutputUtil.Login_Error, e);
        }
        player.getClient().announce(MaplePacketCreator.serverMessage(channelServer.getServerMessage()));
//        player.sendMacros();
        player.showNote();
//        player.sendImp();
        player.updatePartyMemberHP();
//        player.startFairySchedule(false);
//        player.baseSkills(); //fix people who've lost skills.
        c.announce(MaplePacketCreator.getKeymap(player.getKeyLayout()));
//        player.updatePetAuto();
        player.expirationTask(true, transfer == null);
        player.spawnSavedPets();
        player.getStat().recalcLocalStats(true, player);
        if (player.getJob() == 132) { // DARKKNIGHT
            player.checkBerserk();
        }
//        player.spawnClones();
/*        if (player.getStat().equippedSummon > 0) {
            SkillFactory.getSkill(player.getStat().equippedSummon).getEffect(1).applyTo(player);
        }
        MapleQuestStatus stat = player.getQuestNoAdd(MapleQuest.getInstance(GameConstants.PENDANT_SLOT));
        c.announce(MaplePacketCreator.pendantSlot(stat != null && stat.getCustomData() != null && Long.parseLong(stat.getCustomData()) > System.currentTimeMillis()));
        stat = player.getQuestNoAdd(MapleQuest.getInstance(GameConstants.QUICK_SLOT));
        c.announce(MaplePacketCreator.quickSlot(stat != null && stat.getCustomData() != null && stat.getCustomData().length() == 8 ? stat.getCustomData() : null));
        if (GameConstants.GMS) {
            c.announce(MaplePacketCreator.getFamiliarInfo(player));
        }*/
    }

    public static final void ChangeChannel(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.hasBlockedInventory() || chr.getEventInstance() != null || chr.getMap() == null || chr.isInBlockedMap() || FieldLimitType.ChannelSwitch.check(chr.getMap().getFieldLimit())) {
            c.announce(MaplePacketCreator.enableActions());
            return;
        }
        /*        if (World.getPendingCharacterSize() >= 10) {
            chr.dropMessage(1, "The server is busy at the moment. Please try again in a less than a minute.");
            c.announce(MaplePacketCreator.enableActions());
            return;
        }*/
        final int chc = slea.readByte() + 1;
        if (!World.isChannelAvailable(chc)) {
            chr.dropMessage(1, "此頻道已滿人。");
            c.announce(MaplePacketCreator.enableActions());
            return;
        }
        chr.changeChannel(chc);
    }
}
