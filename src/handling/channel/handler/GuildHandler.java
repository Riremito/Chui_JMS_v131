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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;

import client.MapleCharacter;
import client.MapleClient;

import client.Skill;
import client.SkillFactory;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.World;
import handling.world.guild.*;
import java.util.ArrayList;
import java.util.List;
import server.MapleStatEffect;
import server.Timer.MapTimer;
import tools.MaplePacketCreator;
import tools.data.LittleEndianAccessor;
import tools.Pair;

public class GuildHandler {

    public static final void DenyGuildRequest(final String from, final MapleClient c) {
        final MapleCharacter cfrom = c.getChannelServer().getPlayerStorage().getCharacterByName(from);
        if (cfrom != null && invited.remove(c.getPlayer().getName().toLowerCase()) != null) {
            cfrom.getClient().announce(MaplePacketCreator.denyGuildInvitation(c.getPlayer().getName()));
        }
    }

    private static final boolean isGuildNameAcceptable(final String name) {
        if (name.length() < 2/*3*/ || name.length() > 12) {
            return false;
        }
/*        for (int i = 0; i < name.length(); i++) {
            if (!Character.isLowerCase(name.charAt(i)) && !Character.isUpperCase(name.charAt(i))) {
                return false;
            }
        }*/
        return true;
    }

    private static final void respawnPlayer(final MapleCharacter mc) {
        if (mc.getMap() == null) {
            return;
        }
        mc.getMap().broadcastMessage(MaplePacketCreator.loadGuildName(mc));
        mc.getMap().broadcastMessage(MaplePacketCreator.loadGuildIcon(mc));
    }

    private static final Map<String, Pair<Integer, Long>> invited = new HashMap<String, Pair<Integer, Long>>();
    private static long nextPruneTime = System.currentTimeMillis() + 5 * 60 * 1000;

    public static final void Guild(final LittleEndianAccessor slea, final MapleClient c) {
        final long currentTime = System.currentTimeMillis();
        if (currentTime >= nextPruneTime) {
            Iterator<Entry<String, Pair<Integer, Long>>> itr = invited.entrySet().iterator();
            Entry<String, Pair<Integer, Long>> inv;
            while (itr.hasNext()) {
                inv = itr.next();
                if (currentTime >= inv.getValue().right) {
                    itr.remove();
                }
            }
            nextPruneTime += 5 * 60 * 1000;
        }

        switch (slea.readByte()) { //AFTERSHOCK: most are +1
            case 0x02: // Create guild
                final MapleParty party = c.getPlayer().getParty();
                final int leaderchrid = party.getLeader().getId();
                if (c.getPlayer().getGuildId() > 0 || c.getPlayer().getMapId() != 200000301) {
                    c.getPlayer().dropMessage(1, "你已經擁有公會了。");
                    return;
                } else if (c.getPlayer().getMeso() < 5000000) {
                    c.getPlayer().dropMessage(1, "你沒有足夠的楓幣來創建公會。");
                    return;
                } else if (party == null) {
                    c.getPlayer().dropMessage(1, "想要創建公會需要有隊伍。");
                    return;
                } else if (leaderchrid != c.getPlayer().getId()) {
                    c.getPlayer().dropMessage(1, "創建公會需要由隊伍隊長執行。");
                    return;
                } else if (c.getPlayer().getParty().getMembers().size() < 6) {
                    c.getPlayer().dropMessage(1, "請確認你的隊伍是否有六名擁有堅毅信念組成公會的成員之後再來找我。");
                    return;
                }
                final String guildName = slea.readMapleAsciiString();
                if (!isGuildNameAcceptable(guildName)) {
                    c.getPlayer().dropMessage(1, "你選擇的公會名稱我不能接受。");
                    return;
                }
/*                int guildId = World.Guild.createGuild(c.getPlayer().getId(), guildName);
                if (guildId == 0) {
                    c.getPlayer().dropMessage(1, "請再試一次。");
                    return;
                }
                c.getPlayer().gainMeso(-500000, true, true);
                c.getPlayer().setGuildId(guildId);
                c.getPlayer().setGuildRank((byte) 1);
                c.getPlayer().saveGuildStatus();
                c.getPlayer().finishAchievement(35);
                World.Guild.setGuildMemberOnline(c.getPlayer().getMGC(), true, c.getChannel());
                c.announce(MaplePacketCreator.showGuildInfo(c.getPlayer()));
		World.Guild.gainGP(c.getPlayer().getGuildId(), 500, c.getPlayer().getId());
                c.getPlayer().dropMessage(1, "公會已成功創建！");
                respawnPlayer(c.getPlayer());*/
                for (MaplePartyCharacter partychar : party.getMembers()) {
                    partychar.getCharacter().接受公會(false);
                    partychar.getCharacter().getClient().announce(MaplePacketCreator.guildCreate(party.getLeader().getId() == partychar.getId(), party.getId(), c.getPlayer().getName(), guildName));
                }
                MapTimer.getInstance().schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (c.getPlayer().getMeso() < 5000000) {
                            c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                            return;
                        }
                        int 接受 = 0;
                        for (MaplePartyCharacter partychar : party.getMembers()) {
                            if (partychar.getCharacter().getGuildId() != 0) {
                                c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                                return;
                            } else if (party.getMembers().size() < 6) {
                                c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                                return;
                            } else if (partychar.getCharacter().getParty() != party) {
                                c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                                return;
                            } else if (partychar.getChannel() != c.getPlayer().getClient().getChannel()) {
                                c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                                return;
                            } else if (partychar.getMapid() != 200000301) {
                                c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                                return;
                            } else if (leaderchrid != party.getLeader().getId()) {
                                c.announce(MapleGuildResponse.創立時發生問題.getPacket());
                                return;
                            } else if (partychar.getCharacter().是否接受公會()) {
                                接受++;
                            }
                        }
                        if (接受 != 5) {
                            c.announce(MapleGuildResponse.有人不同意.getPacket());
                            return;
                        }
                        int guildId = World.Guild.createGuild(leaderchrid, guildName);
                        if (guildId == 0) {
                            c.getPlayer().dropMessage(1, "請再試一次。");
                            return;
                        }
                        c.getPlayer().gainMeso(-5000000, true, true);
                        final MapleCharacter leaderchr = party.getLeader().getCharacter();
                        leaderchr.setGuildId(guildId);
                        leaderchr.setGuildRank((byte) 1);
                        leaderchr.saveGuildStatus();
                        World.Guild.setGuildMemberOnline(leaderchr.getMGC(), true, leaderchr.getClient().getChannel());
//                        leaderchr.getClient().announce(MaplePacketCreator.showGuildInfo(leaderchr));
//                        respawnPlayer(leaderchr);
                        for (MaplePartyCharacter pchr : party.getMembers()) {
                            if (party.getLeader().getId() != pchr.getId()) {
                                pchr.getCharacter().setGuildId(guildId);
                                pchr.getCharacter().setGuildRank((byte) 5/*3*/);
                                pchr.getCharacter().saveGuildStatus();
//                                World.Guild.setGuildMemberOnline(pchr.getCharacter().getMGC(), true, pchr.getChannel());
//                                pchr.getCharacter().getClient().announce(MaplePacketCreator.showGuildInfo(pchr.getCharacter()));
//                                respawnPlayer(pchr.getCharacter());
                            }
                        }
                        for (MaplePartyCharacter pchr : party.getMembers()) {
                            if (party.getLeader().getId() != pchr.getId()) {
                                World.Guild.setGuildMemberOnline(pchr.getCharacter().getMGC(), true, pchr.getChannel());
                            }
                        }
                        for (MaplePartyCharacter pchr : party.getMembers()) {
                            pchr.getCharacter().getClient().announce(MaplePacketCreator.showGuildInfo(pchr.getCharacter()));
                            respawnPlayer(pchr.getCharacter());
                            pchr.getCharacter().接受公會(false);
                            pchr.getCharacter().dropMessage(5, "已加入 '" + guildName + "' 公會！");
                            pchr.getCharacter().dropMessage(5, "'" + guildName + "' 公會 已成功創建！");
                        }
                    }
                }, 20000/*40000*/); // 20秒
                break;
            case 0x05: // invitation
                if (c.getPlayer().getGuildId() <= 0 || c.getPlayer().getGuildRank() > 2) { // 1 == guild master, 2 == jr
                    return;
                }
                String name = slea.readMapleAsciiString().toLowerCase();
                if (invited.containsKey(name)) {
                    c.getPlayer().dropMessage(5, "The player is currently handling an invitation.");
                    return;
                }
                final MapleGuildResponse mgr = MapleGuild.sendInvite(c, name);
                if (mgr != null) {
                    c.announce(mgr.getPacket());
                } else {
                    invited.put(name, new Pair<Integer, Long>(c.getPlayer().getGuildId(), currentTime + (20 * 60000))); //20 mins expire
                }
                break;
            case 0x06: // accepted guild invitation
                if (c.getPlayer().getGuildId() > 0) {
                    return;
                }
                int guildId = slea.readInt();
                int cid = slea.readInt();
                if (cid != c.getPlayer().getId()) {
                    return;
                }
                name = c.getPlayer().getName().toLowerCase();
                Pair<Integer, Long> gid = invited.remove(name);
                if (gid != null && guildId == gid.left) {
                    c.getPlayer().setGuildId(guildId);
                    c.getPlayer().setGuildRank((byte) 5);
                    int s = World.Guild.addGuildMember(c.getPlayer().getMGC());
                    if (s == 0) {
                        c.getPlayer().dropMessage(1, "The Guild you are trying to join is already full.");
                        c.getPlayer().setGuildId(0);
                        return;
                    }
                    c.announce(MaplePacketCreator.showGuildInfo(c.getPlayer()));
                    final MapleGuild gs = World.Guild.getGuild(guildId);
                    for (byte[] pack : World.Alliance.getAllianceInfo(gs.getAllianceId(), true)) {
                        if (pack != null) {
                            c.announce(pack);
                        }
                    }
                    c.getPlayer().saveGuildStatus();
                    respawnPlayer(c.getPlayer());
                }
                break;
            case 0x07: // leaving
                cid = slea.readInt();
                name = slea.readMapleAsciiString();

                if (cid != c.getPlayer().getId() || !name.equals(c.getPlayer().getName()) || c.getPlayer().getGuildId() <= 0) {
                    return;
                }
                World.Guild.leaveGuild(c.getPlayer().getMGC());
                c.announce(MaplePacketCreator.showGuildInfo(null));
                break;
            case 0x08: // Expel
                cid = slea.readInt();
                name = slea.readMapleAsciiString();

                if (c.getPlayer().getGuildRank() > 2 || c.getPlayer().getGuildId() <= 0) {
                    return;
                }
                World.Guild.expelMember(c.getPlayer().getMGC(), name, cid);
                break;
            case 0x0D: // Guild rank titles change
                if (c.getPlayer().getGuildId() <= 0 || c.getPlayer().getGuildRank() != 1) {
                    return;
                }
                String ranks[] = new String[5];
                for (int i = 0; i < 5; i++) {
                    ranks[i] = slea.readMapleAsciiString();
                }
                World.Guild.changeRankTitle(c.getPlayer().getGuildId(), ranks);
                break;
            case 0x0E: // Rank change
                cid = slea.readInt();
                byte newRank = slea.readByte();
                if ((newRank <= 1 || newRank > 5) || c.getPlayer().getGuildRank() > 2 || (newRank <= 2 && c.getPlayer().getGuildRank() != 1) || c.getPlayer().getGuildId() <= 0) {
                    return;
                }
                World.Guild.changeRank(c.getPlayer().getGuildId(), cid, newRank);
                break;
            case 0x0F: // guild emblem change
                if (c.getPlayer().getGuildId() <= 0 || c.getPlayer().getGuildRank() != 1 || c.getPlayer().getMapId() != 200000301) {
                    return;
                }
                if (c.getPlayer().getMeso() < 1500000) {
                    c.getPlayer().dropMessage(1, "メルが足りない。");
                    return;
                }
                final short bg = slea.readShort();
                final byte bgcolor = slea.readByte();
                final short logo = slea.readShort();
                final byte logocolor = slea.readByte();
                World.Guild.setGuildEmblem(c.getPlayer().getGuildId(), bg, bgcolor, logo, logocolor);
                c.getPlayer().gainMeso(-1500000, true, true);
                respawnPlayer(c.getPlayer());
                break;
            case 0x10: // guild notice change
                final String notice = slea.readMapleAsciiString();
                if (notice.length() > 100 || c.getPlayer().getGuildId() <= 0 || c.getPlayer().getGuildRank() > 2) {
                    return;
                }
                World.Guild.setGuildNotice(c.getPlayer().getGuildId(), notice);
                break;
            case 0x1E: // guild create
                slea.readInt(); // partyid
                boolean 接受 = slea.readByte() > 0;
                c.getPlayer().接受公會(接受);
                break;
/*            case 0x1d: //guild skill purchase
                Skill skilli = SkillFactory.getSkill(slea.readInt());
                if (c.getPlayer().getGuildId() <= 0 || skilli == null || skilli.getId() < 91000000) {
                    return;
                }
                int eff = World.Guild.getSkillLevel(c.getPlayer().getGuildId(), skilli.getId()) + 1;
                if (eff > skilli.getMaxLevel()) {
                    return;
                }
                final MapleStatEffect skillid = skilli.getEffect(eff);
                if (skillid.getReqGuildLevel() <= 0 || c.getPlayer().getMeso() < skillid.getPrice()) {
                    return;
                }
                if (World.Guild.purchaseSkill(c.getPlayer().getGuildId(), skillid.getSourceId(), c.getPlayer().getName(), c.getPlayer().getId())) {
                    c.getPlayer().gainMeso(-skillid.getPrice(), true);
                }
                break;
            case 0x1e: //guild skill activation
                skilli = SkillFactory.getSkill(slea.readInt());
                if (c.getPlayer().getGuildId() <= 0 || skilli == null) {
                    return;
                }
                eff = World.Guild.getSkillLevel(c.getPlayer().getGuildId(), skilli.getId());
                if (eff <= 0) {
                    return;
                }
                final MapleStatEffect skillii = skilli.getEffect(eff);
                if (skillii.getReqGuildLevel() < 0 || c.getPlayer().getMeso() < skillii.getExtendPrice()) {
                    return;
                }
                if (World.Guild.activateSkill(c.getPlayer().getGuildId(), skillii.getSourceId(), c.getPlayer().getName())) {
                    c.getPlayer().gainMeso(-skillii.getExtendPrice(), true);
                }
                break;
            case 0x1f: //guild leader change
                cid = slea.readInt();
                if (c.getPlayer().getGuildId() <= 0 || c.getPlayer().getGuildRank() > 1) {
                    return;
                }
                World.Guild.setGuildLeader(c.getPlayer().getGuildId(), cid);
                break;*/
        }
    }
}
