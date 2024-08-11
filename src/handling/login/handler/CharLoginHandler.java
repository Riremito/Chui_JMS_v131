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
package handling.login.handler;

import java.util.List;
import java.util.Calendar;

import client.inventory.Item;
import client.LoginCrypto;
import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import constants.GameConstants;
import client.SkillFactory;
import constants.ServerConstants;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginInformationProvider;
import handling.login.LoginInformationProvider.JobType;
import handling.login.LoginServer;
import handling.login.LoginWorker;
import handling.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import server.MapleItemInformationProvider;
import server.quest.MapleQuest;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.packet.LoginPacket;
import tools.data.LittleEndianAccessor;
import tools.packet.PacketHelper;

public class CharLoginHandler {

    private static final boolean loginFailCount(final MapleClient c) {
        c.loginAttempt++;
        if (c.loginAttempt > 5) {
            return true;
        }
        return false;
    }

    public static final void login(final LittleEndianAccessor slea, final MapleClient c) {
        String login = slea.readMapleAsciiString();
        String pwd = slea.readMapleAsciiString();

        final boolean ipBan = c.hasBannedIP();
        final boolean macBan = c.hasBannedMac();

        int loginok = c.login(login, pwd, ipBan || macBan);
        final Calendar tempbannedTill = c.getTempBanCalendar();

        if (loginok == 0 && (ipBan || macBan) && !c.isGm()) {
            loginok = 3;
            if (macBan) {
                // this is only an ipban o.O" - maybe we should refactor this a bit so it's more readable
                MapleCharacter.ban(c.getSession().remoteAddress().toString().split(":")[0], "Enforcing account ban, account " + login, false, 4, false);
            }
        }
        if (loginok != 0) {
            if (!loginFailCount(c)) {
                c.clearInformation();
                c.announce(LoginPacket.getLoginFailed(loginok));
            } else {
                c.getSession().close();
            }
        } else if (tempbannedTill.getTimeInMillis() != 0) {
            if (!loginFailCount(c)) {
                c.clearInformation();
                c.announce(LoginPacket.getTempBan(PacketHelper.getTime(tempbannedTill.getTimeInMillis()), c.getBanReason()));
            } else {
                c.getSession().close();
            }
        } else {
            c.loginAttempt = 0;
            LoginWorker.registerClient(c);
        }
    }

    public static void SetGenderRequest(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.hasBannedIP()) {
            c.getSession().close();
            return;
        }
        String username = slea.readMapleAsciiString();
        String password = slea.readMapleAsciiString();
        if (c.getAccountName().equals(username)) {
            c.setGender(slea.readByte());
            c.setSecondPassword(password);
            c.updateSecondPassword();
            c.updateGender();
            c.announce(LoginPacket.getGenderChanged(c));
            c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
        } else {
            c.getSession().close();
        }
    }

    public static final void ServerListRequest(final MapleClient c) {
//        c.announce(LoginPacket.getLoginWelcome());
        c.announce(LoginPacket.getServerListNull(0));
        c.announce(LoginPacket.getServerListNull(1));
        c.announce(LoginPacket.getServerListNull(2));
        c.announce(LoginPacket.getServerListNull(3));
        c.announce(LoginPacket.getServerListNull(4));
        c.announce(LoginPacket.getServerList(LoginServer.getMainServerId(), LoginServer.getLoad()));
        c.announce(LoginPacket.getServerListNull(6));
        c.announce(LoginPacket.getServerListNull(7));
        c.announce(LoginPacket.getServerListNull(8));
        c.announce(LoginPacket.getServerListNull(9));
        //c.announce(MaplePacketCreator.getServerList(1, "Scania", LoginServer.getInstance().getChannels(), 1200));
        c.announce(LoginPacket.getEndOfServerList());
//        c.announce(LoginPacket.enableRecommended());
//        c.announce(LoginPacket.sendRecommended(0, LoginServer.getEventMessage()));
    }

    public static final void ServerStatusRequest(final MapleClient c) {
        // 0 = Select world normally
        // 1 = "Since there are many users, you may encounter some..."
        // 2 = "The concurrent users in this world have reached the max"
        final int numPlayer = LoginServer.getUsersOn();
        final int userLimit = LoginServer.getUserLimit();
        if (numPlayer >= userLimit) {
            c.announce(LoginPacket.getServerStatus(2));
        } else if (numPlayer * 2 >= userLimit) {
            c.announce(LoginPacket.getServerStatus(1));
        } else {
            c.announce(LoginPacket.getServerStatus(0));
        }
    }

    public static final void CharlistRequest(final LittleEndianAccessor slea, final MapleClient c) {
        if (!c.isLoggedIn()) {
            c.getSession().close();
            return;
        }
        if (GameConstants.GMS) {
            slea.readByte(); // ?
        }
        final int server = slea.readByte();
        final int channel = slea.readByte() + 1;
        if (!World.isChannelAvailable(channel) || server != LoginServer.getMainServerId()) { //TODOO: MULTI WORLDS
            c.announce(LoginPacket.getLoginFailed(10)); //cannot process so many
            return;
        }
        //System.out.println("Client " + c.getSession().getRemoteAddress().toString().split(":")[0] + " is connecting to server " + server + " channel " + channel + "");
        final List<MapleCharacter> chars = c.loadCharacters(server);
        if (chars != null && ChannelServer.getInstance(channel) != null) {
            c.setWorld(server);
            c.setChannel(channel);
            c.announce(LoginPacket.getCharList(c.getSecondPassword(), chars, c.getCharacterSlots()));
        } else {
            c.getSession().close();
        }
    }

    public static final void CheckCharName(final String name, final MapleClient c) {
        c.announce(LoginPacket.charNameResponse(name,
                !(MapleCharacterUtil.canCreateChar(name, c.isGm()) && (!LoginInformationProvider.getInstance().isForbiddenName(name) || c.isGm()))));
    }

    public static final void CreateChar(final LittleEndianAccessor slea, final MapleClient c) {
        if (!c.isLoggedIn()) {
            c.getSession().close();
            return;
        }
        final String name = slea.readMapleAsciiString();
        final JobType jobType = JobType.getByType(1); // BIGBANG: 0 = Resistance, 1 = Adventurer, 2 = Cygnus, 3 = Aran, 4 = Evan
        final short db = 0; //whether dual blade = 1 or adventurer = 0
        final byte gender = c.getGender(); //??idk corresponds with the thing in addCharStats
        final int face = slea.readInt();
        final int hair = slea.readInt();
        final int hairColor = 0;
        final byte skinColor = (byte) 0;
        final int top = slea.readInt();
        final int bottom = slea.readInt();
        final int shoes = slea.readInt();
        final int weapon = slea.readInt();
        final short str = slea.readByte();
        final short dex = slea.readByte();
        final short int_ = slea.readByte();
        final short luk = slea.readByte();

        if (!LoginInformationProvider.getInstance().isEligibleItem(gender, 0, jobType.type, face) || !LoginInformationProvider.getInstance().isEligibleItem(gender, 1, jobType.type, hair)
//                || !LoginInformationProvider.getInstance().isEligibleItem(gender, 2, jobType.type, hairColor) || !LoginInformationProvider.getInstance().isEligibleItem(gender, 3, jobType.type, skinColor)
                || !LoginInformationProvider.getInstance().isEligibleItem(gender, 2, jobType.type, top) || !LoginInformationProvider.getInstance().isEligibleItem(gender, 3, jobType.type, bottom)
                || !LoginInformationProvider.getInstance().isEligibleItem(gender, 4, jobType.type, shoes) || !LoginInformationProvider.getInstance().isEligibleItem(gender, 5, jobType.type, weapon)) {
            return;
        }

        MapleCharacter newchar = MapleCharacter.getDefault(c, jobType);
        newchar.setWorld((byte) c.getWorld());
        newchar.setFace(face);
        newchar.setHair(hair + hairColor);
        newchar.setGender(gender);
        newchar.setName(name);
        newchar.setSkinColor(skinColor);
        newchar.getStat().str = str;
        newchar.getStat().dex = dex;
        newchar.getStat().int_ = int_;
        newchar.getStat().luk = luk;

        final MapleItemInformationProvider li = MapleItemInformationProvider.getInstance();
        final MapleInventory equip = newchar.getInventory(MapleInventoryType.EQUIPPED);
        Item item = li.getEquipById(top);
        item.setPosition((byte) -5);
        equip.addFromDB(item);

        if (bottom > 0) { //resistance have overall
            item = li.getEquipById(bottom);
            item.setPosition((byte) -6);
            equip.addFromDB(item);
        }

        item = li.getEquipById(shoes);
        item.setPosition((byte) -7);
        equip.addFromDB(item);

        item = li.getEquipById(weapon);
        item.setPosition((byte) -11);
        equip.addFromDB(item);

        if (MapleCharacterUtil.canCreateChar(name, c.isGm()) && (!LoginInformationProvider.getInstance().isForbiddenName(name) || c.isGm()) && (c.isGm() || c.canMakeCharacter(c.getWorld()))) {
            MapleCharacter.saveNewCharToDB(newchar, jobType, jobType.id == 0 ? db : 0);
            c.announce(LoginPacket.addNewCharEntry(newchar, true));
            c.createdChar(newchar.getId());
        } else {
            c.announce(LoginPacket.addNewCharEntry(newchar, false));
        }
    }

    public static final void CreateUltimate(final LittleEndianAccessor slea, final MapleClient c) {
        if (!c.isLoggedIn() || c.getPlayer() == null || c.getPlayer().getLevel() < 120 || c.getPlayer().getMapId() != 130000000 || c.getPlayer().getQuestStatus(20734) != 0 || c.getPlayer().getQuestStatus(20616) != 2 || !GameConstants.isKOC(c.getPlayer().getJob()) || !c.canMakeCharacter(c.getPlayer().getWorld())) {
            c.getPlayer().dropMessage(1, "You have no character slots.");
            c.announce(MaplePacketCreator.createUltimate(0));
            return;
        }
        final String name = slea.readMapleAsciiString();
        final int job = slea.readInt(); //job ID
        if (job < 110 || job > 520 || job % 10 > 0 || (job % 100 != 10 && job % 100 != 20 && job % 100 != 30) || job == 430) {
            c.getPlayer().dropMessage(1, "An error has occurred.");
            c.announce(MaplePacketCreator.createUltimate(0));
            return;
        }
        final int face = slea.readInt();
        final int hair = slea.readInt();

        final int hat = slea.readInt();
        final int top = slea.readInt();
        final int glove = slea.readInt();
        final int shoes = slea.readInt();
        final int weapon = slea.readInt();

        final byte gender = c.getPlayer().getGender();
        JobType jobType = JobType.Adventurer;
        if (!LoginInformationProvider.getInstance().isEligibleItem(gender, 0, jobType.type, face) || !LoginInformationProvider.getInstance().isEligibleItem(gender, 1, jobType.type, hair)) {
            c.getPlayer().dropMessage(1, "An error occurred.");
            c.announce(MaplePacketCreator.createUltimate(0));
            return;
        }

        jobType = JobType.UltimateAdventurer;
        if (!LoginInformationProvider.getInstance().isEligibleItem(-1, job, jobType.type, hat) || !LoginInformationProvider.getInstance().isEligibleItem(-1, job, jobType.type, top)
                || !LoginInformationProvider.getInstance().isEligibleItem(-1, job, jobType.type, glove) || !LoginInformationProvider.getInstance().isEligibleItem(-1, job, jobType.type, shoes)
                || !LoginInformationProvider.getInstance().isEligibleItem(-1, job, jobType.type, weapon)) {
            c.getPlayer().dropMessage(1, "An error occured.");
            c.announce(MaplePacketCreator.createUltimate(0));
            return;
        }

        MapleCharacter newchar = MapleCharacter.getDefault(c, jobType);
        newchar.setJob(job);
        newchar.setWorld((byte) c.getPlayer().getWorld());
        newchar.setFace(face);
        newchar.setHair(hair);
        newchar.setGender(gender);
        newchar.setName(name);
        newchar.setSkinColor((byte) 3); //troll
        newchar.setLevel((short) 51);
        newchar.getStat().str = (short) 4;
        newchar.getStat().dex = (short) 4;
        newchar.getStat().int_ = (short) 4;
        newchar.getStat().luk = (short) 4;
        newchar.setRemainingAp((short) 254); //49*5 + 25 - 16
        newchar.setRemainingSp(job / 100 == 2 ? 128 : 122); //2 from job advancements. 120 from leveling. (mages get +6)
        newchar.getStat().maxhp += 150; //Beginner 10 levels
        newchar.getStat().maxmp += 125;
        switch (job) {
            case 110:
            case 120:
            case 130:
                newchar.getStat().maxhp += 600; //Job Advancement
                newchar.getStat().maxhp += 2000; //Levelup 40 times
                newchar.getStat().maxmp += 200;
                break;
            case 210:
            case 220:
            case 230:
                newchar.getStat().maxmp += 600;
                newchar.getStat().maxhp += 500; //Levelup 40 times
                newchar.getStat().maxmp += 2000;
                break;
            case 310:
            case 320:
            case 410:
            case 420:
            case 520:
                newchar.getStat().maxhp += 500;
                newchar.getStat().maxmp += 250;
                newchar.getStat().maxhp += 900; //Levelup 40 times
                newchar.getStat().maxmp += 600;
                break;
            case 510:
                newchar.getStat().maxhp += 500;
                newchar.getStat().maxmp += 250;
                newchar.getStat().maxhp += 450; //Levelup 20 times
                newchar.getStat().maxmp += 300;
                newchar.getStat().maxhp += 800; //Levelup 20 times
                newchar.getStat().maxmp += 400;
                break;
            default:
                return;
        }
        for (int i = 2490; i < 2507; i++) {
            newchar.setQuestAdd(MapleQuest.getInstance(i), (byte) 2, null);
        }
        newchar.setQuestAdd(MapleQuest.getInstance(29947), (byte) 2, null);
        newchar.setQuestAdd(MapleQuest.getInstance(GameConstants.ULT_EXPLORER), (byte) 0, c.getPlayer().getName());
        newchar.changeSkillLevel_Skip(SkillFactory.getSkill(1074 + (job / 100)), (byte) 5, (byte) 5);
        newchar.changeSkillLevel_Skip(SkillFactory.getSkill(80), (byte) 1, (byte) 1);
        final MapleItemInformationProvider li = MapleItemInformationProvider.getInstance();
        int[] items = new int[]{1142257, hat, top, shoes, glove, weapon, hat + 1, top + 1, shoes + 1, glove + 1, weapon + 1}; //brilliant = fine+1
        for (byte i = 0; i < items.length; i++) {
            Item item = li.getEquipById(items[i]);
            item.setPosition((byte) (i + 1));
            newchar.getInventory(MapleInventoryType.EQUIP).addFromDB(item);
        }
        newchar.getInventory(MapleInventoryType.USE).addItem(new Item(2000004, (byte) 0, (short) 100, (byte) 0));
        newchar.getInventory(MapleInventoryType.USE).addItem(new Item(2000004, (byte) 0, (short) 100, (byte) 0));
        c.getPlayer().fakeRelog();
        if (MapleCharacterUtil.canCreateChar(name, c.isGm()) && (!LoginInformationProvider.getInstance().isForbiddenName(name) || c.isGm())) {
            MapleCharacter.saveNewCharToDB(newchar, jobType, (short)0);
            MapleQuest.getInstance(20734).forceComplete(c.getPlayer(), 1101000);
            c.announce(MaplePacketCreator.createUltimate(1));
        } else {
            c.announce(MaplePacketCreator.createUltimate(0));
        }
    }

    public static final void DeleteChar(final LittleEndianAccessor slea, final MapleClient c) {
        String Secondpw_Client = slea.readMapleAsciiString();
        final int Character_ID = slea.readInt();
        if (!c.login_Auth(Character_ID) || !c.isLoggedIn() || loginFailCount(c)) {
            c.getSession().close();
            return; // Attempting to delete other character
        }
        byte state = 0;
        if (c.getSecondPassword() != null) { // On the server, there's a second password
            if (Secondpw_Client == null) { // Client's hacking
                c.getSession().close();
                return;
            } else {
                if (!c.CheckSecondPassword(Secondpw_Client)) { // Wrong Password
                    state = 12;
                }
            }
        }
        if (state == 0) {
            state = (byte) c.deleteCharacter(Character_ID);
        }
        c.announce(LoginPacket.deleteCharResponse(Character_ID, state));
    }

    public static final void Character_WithoutSecondPassword(final LittleEndianAccessor slea, final MapleClient c, final boolean haspic, final boolean view) {
        if (c.getPlayer() != null) {
            return;
        }
        final int charId = slea.readInt();
        if (World.Find.findChannel(charId) != -1 || CashShopServer.getPlayerStorage().getPendingCharacter(charId) != null) {
            c.getSession().close();
            return;
        }
        MapleCharacter chr = CashShopServer.getPlayerStorage().getCharacterById(charId);
        if (chr != null) {
            chr.getClient().getSession().close();
            c.getSession().close();
            return;
        }
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            if (cs.getPlayerStorage().getPendingCharacter(charId) != null) {
                c.getSession().close();
                return;
            }
            chr = cs.getPlayerStorage().getCharacterById(charId);
            if (chr != null) {
                chr.getClient().getSession().close();
                c.getSession().close();
                return;
            }
        }
//        final String currentpw = c.getSecondPassword();
        if (!c.isLoggedIn() || loginFailCount(c) /*|| (currentpw != null && (!currentpw.equals("") || haspic)) */|| !c.login_Auth(charId) || ChannelServer.getInstance(c.getChannel()) == null || c.getWorld() != LoginServer.getMainServerId()) { // TODOO: MULTI WORLDS
            c.getSession().close();
            return;
        }

/*        if (c.getIdleTask() != null) {
            c.getIdleTask().cancel(true);
        }*/
        final String s = c.getSessionIPAddress();
        LoginServer.putLoginAuth(charId, s.substring(s.indexOf('/') + 1, s.length()), c.getTempIP());
        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, s);
        c.announce(MaplePacketCreator.getServerIP(c, Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
    }

    public static final void Character_WithSecondPassword(final LittleEndianAccessor slea, final MapleClient c, final boolean view) {
        if (c.getPlayer() != null) {
            return;
        }
        final String password = slea.readMapleAsciiString();
        final int charId = slea.readInt();
        if (World.Find.findChannel(charId) != -1) {
            c.getSession().close();
            return;
        }
        if (!c.isLoggedIn() || loginFailCount(c) || c.getSecondPassword() == null || !c.login_Auth(charId) || ChannelServer.getInstance(c.getChannel()) == null || c.getWorld() != LoginServer.getMainServerId()) { // TODOO: MULTI WORLDS
            c.getSession().close();
            return;
        }
        if (c.CheckSecondPassword(password) && password.length() >= 6 && password.length() <= 16) {
/*            if (c.getIdleTask() != null) {
                c.getIdleTask().cancel(true);
            }*/
            final String s = c.getSessionIPAddress();
            LoginServer.putLoginAuth(charId, s.substring(s.indexOf('/') + 1, s.length()), c.getTempIP());
            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, s);
            c.announce(MaplePacketCreator.getServerIP(c, Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1]), charId));
        } else {
            c.announce(LoginPacket.secondPwError((byte) 0x14));
        }
    }

    public static void ViewChar(LittleEndianAccessor slea, MapleClient c) {
        Map<Byte, ArrayList<MapleCharacter>> worlds = new HashMap<Byte, ArrayList<MapleCharacter>>();
        List<MapleCharacter> chars = c.loadCharacters(LoginServer.getMainServerId()); //TODO multi world
        c.announce(LoginPacket.showAllCharacter(chars.size()));
        for (MapleCharacter chr : chars) {
            if (chr != null) {
                ArrayList<MapleCharacter> chrr;
                if (!worlds.containsKey(chr.getWorld())) {
                    chrr = new ArrayList<MapleCharacter>();
                    worlds.put(chr.getWorld(), chrr);
                } else {
                    chrr = worlds.get(chr.getWorld());
                }
                chrr.add(chr);
            }
        }
        for (Entry<Byte, ArrayList<MapleCharacter>> w : worlds.entrySet()) {
            c.announce(LoginPacket.showAllCharacterInfo(w.getKey(), w.getValue(), c.getSecondPassword()));
        }
    }
}
