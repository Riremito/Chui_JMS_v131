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
package handling;

import constants.ServerConstants;

import client.MapleClient;
import client.inventory.MaplePet;
import client.inventory.PetDataFactory;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.cashshop.handler.*;
import handling.channel.handler.*;
import handling.login.LoginServer;
import handling.login.handler.*;
import handling.netty.MaplePacketDecoder;
import tools.MapleAESOFB;
import tools.packet.LoginPacket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import server.MTSStorage;
import tools.FileoutputUtil;
import tools.HexTool;
import tools.data.LittleEndianAccessor;
import tools.data.ByteArrayByteStream;

public class MapleServerHandler extends ChannelInboundHandlerAdapter {

//    public static boolean Log_Packets = false;
    private int channel = -1;
//    private static int numDC = 0;
//    private static long lastDC = System.currentTimeMillis();
//    private boolean cs;
//    private final List<String> BlockedIP = new ArrayList<String>();
//    private final Map<String, Pair<Long, Byte>> tracker = new ConcurrentHashMap<String, Pair<Long, Byte>>();
    //Screw locking. Doesn't matter.
//    private static final ReentrantReadWriteLock IPLoggingLock = new ReentrantReadWriteLock();
//    private static final String nl = System.getProperty("line.separator");
//    private static final File loggedIPs = new File("LogIPs.txt");
//    private static final HashMap<String, FileWriter> logIPMap = new HashMap<String, FileWriter>();
    //Note to Zero: Use an enumset. Don't iterate through an array.
//    private static final EnumSet<RecvPacketOpcode> blocked = EnumSet.noneOf(RecvPacketOpcode.class), sBlocked = EnumSet.noneOf(RecvPacketOpcode.class);

    public MapleServerHandler(final int channel) {
        this.channel = channel;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        // Start of IP checking
        String address = ctx.channel().remoteAddress().toString().split(":")[0];
        /*        if (BlockedIP.contains(address)) {
            ctx.channel().close();
            return;
        }
        final Pair<Long, Byte> track = tracker.get(address);
        byte count;
        if (track == null) {
            count = 1;
        } else {
            count = track.right;
            final long difference = System.currentTimeMillis() - track.left;
            if (difference < 2000) { // Less than 2 sec
                count++;
            } else if (difference > 20000) { // Over 20 sec
                count = 1;
            }
            if (count >= 5) {
                BlockedIP.add(address);
                tracker.remove(address); // Cleanup
                ctx.channel().close();
                return;
            }
        }
        tracker.put(address, new Pair<Long, Byte>(System.currentTimeMillis(), count));*/
        // End of IP checking.
        String IP = address.substring(address.indexOf('/') + 1, address.length());
        if (channel > -1) {
            if (ChannelServer.getInstance(channel).isShutdown()) {
                ctx.channel().close();
                return;
            }
            if (!LoginServer.containsIPAuth(IP)) {
                ctx.channel().close();
                return;
            }
        } else if (channel == -10) {
            if (CashShopServer.isShutdown()) {
                ctx.channel().close();
                return;
            }
            if (!LoginServer.containsIPAuth(IP)) {
                ctx.channel().close();
                return;
            }
        } else {
            if (LoginServer.isShutdown()) {
                ctx.channel().close();
                return;
            }
        }
//        LoginServer.removeIPAuth(IP);

//        final byte ivRecv[] = new byte[]{(byte) Randomizer.nextInt(255), (byte) Randomizer.nextInt(255), (byte) Randomizer.nextInt(255), (byte) Randomizer.nextInt(255)};
//        final byte ivSend[] = new byte[]{(byte) Randomizer.nextInt(255), (byte) Randomizer.nextInt(255), (byte) Randomizer.nextInt(255), (byte) Randomizer.nextInt(255)};
        byte ivRecv[] = {70, 114, 122, (byte) (Math.random() * 255)};
        byte ivSend[] = {82, 48, 120, (byte) (Math.random() * 255)};
//        MapleAESOFB sendCypher = new MapleAESOFB(ivSend, (short) (0xFFFF - ServerConstants.MAPLE_VERSION));
//        MapleAESOFB recvCypher = new MapleAESOFB(ivRecv, ServerConstants.MAPLE_VERSION);
//        MapleClient client = new MapleClient(sendCypher, recvCypher, ctx.channel());
        final MapleClient client = new MapleClient(
                new MapleAESOFB(ivSend, (short) (0xFFFF - ServerConstants.MAPLE_VERSION)), // Sent Cypher
                new MapleAESOFB(ivRecv, ServerConstants.MAPLE_VERSION), // Recv Cypher
                ctx.channel());

        client.setChannel(channel);

        MaplePacketDecoder.DecoderState decoderState = new MaplePacketDecoder.DecoderState();
        ctx.channel().attr(MaplePacketDecoder.DECODER_STATE_KEY).set(decoderState);
        ctx.channel().writeAndFlush(LoginPacket.getHello(ServerConstants.MAPLE_VERSION, ivSend, ivRecv));
        ctx.channel().attr(MapleClient.CLIENT_KEY).set(client);
        /*        if (!ServerConstants.Use_Fixed_IV) {
            RecvPacketOpcode.reloadValues();
            SendPacketOpcode.reloadValues();
        }*/
        StringBuilder sb = new StringBuilder();
        if (channel > -1) {
            sb.append("[Channel Server] Channel ").append(channel).append(" : ");
        } else if (channel == -10) {
            sb.append("[Cash Server]");
        } else {
            sb.append("[Login Server]");
        }
        sb.append("IoSession opened ").append(address);
        System.out.println(sb.toString());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        final MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client != null) {
            try {
                client.disconnect(true, channel == -10);
            } finally {
                ctx.channel().attr(MapleClient.CLIENT_KEY).set(null);
                ctx.channel().attr(MaplePacketDecoder.DECODER_STATE_KEY).set(null);
                ctx.channel().close();
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        /*        MapleClient client = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (client != null) {
            client.sendPing();
        }*/
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg == null || ctx.channel() == null || LoginServer.isShutdown()) {
//            ctx.channel().close();
            return;
        }
        final MapleClient c = ctx.channel().attr(MapleClient.CLIENT_KEY).get();
        if (c == null || !c.isReceiving()) {
//            ctx.channel().close();
            return;
        }
        final LittleEndianAccessor slea = new LittleEndianAccessor(new ByteArrayByteStream((byte[]) msg));
        if (slea.available() < 2) {
//            ctx.channel().close();
            return;
        }
        final short head = slea.readShort();
        if (head == 0x0D) {
            return;
        }
        for (final RecvPacketOpcode recv : RecvPacketOpcode.values()) {

            // login button
            if (recv.getValue() == head && head == RecvPacketOpcode.GAMEGUARD.getValue()) {
                c.announce(LoginPacket.getGameGuardUpdate());
            }

            if (recv.getValue() == head) {
                if (recv.NeedsChecking()) {
                    if (!c.isLoggedIn()) {
//                        ctx.channel().close();
                        return;
                    }
                }
                try {
                    if (head != 0x18 && head != 0x3D && head != 0x7D /*&& head != 0x7E */ && head != 0x85 && ServerConstants.ShowRecvP) {
                        final StringBuilder sb = new StringBuilder("[接收] ");
                        sb.append(recv.toString()).append("\n").append(HexTool.toString((byte[]) msg)).append("\n").append(HexTool.toStringFromAscii((byte[]) msg));
                        System.out.println(sb.toString());
                    }
                    //no login packets
/*                    if (Log_Packets && !blocked.contains(recv) && !sBlocked.contains(recv) && (cs || channel > -1)) {
                        log(slea.toString(), recv.toString(), c, session);
                    }*/
                    handlePacket(recv, slea, c, channel == -10);
                    //Log after the packet is handle. You'll see why =]
/*                    FileWriter fw = isLoggedIP(session);
                    if (fw != null && !blocked.contains(recv)) {
                        if (recv == RecvPacketOpcode.PLAYER_LOGGEDIN && c != null) { // << This is why. Win.
                            fw.write(">> [AccountName: "
                                    + (c.getAccountName() == null ? "null" : c.getAccountName()) + "] | [IGN: "
                                    + (c.getPlayer() == null || c.getPlayer().getName() == null ? "null" : c.getPlayer().getName()) + "] | [Time: "
                                    + FileoutputUtil.CurrentReadable_Time() + "]");
                            fw.write(nl);
                        }
                        fw.write("[" + recv.toString() + "]" + slea.toString(true));
                        fw.write(nl);
                        fw.flush();
                    }*/
                } catch (NegativeArraySizeException e) {
                    //swallow, no one cares
//                    if (!ServerConstants.Use_Fixed_IV) {
                    FileoutputUtil.outputFileError(FileoutputUtil.PacketEx_Log, e);
                    FileoutputUtil.log(FileoutputUtil.PacketEx_Log, "Packet: " + head + "\n" + slea.toString(true));
//                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    //swallow, no one cares
//                    if (!ServerConstants.Use_Fixed_IV) {
                    FileoutputUtil.outputFileError(FileoutputUtil.PacketEx_Log, e);
                    FileoutputUtil.log(FileoutputUtil.PacketEx_Log, "Packet: " + head + "\n" + slea.toString(true));
//                    }
                } catch (Exception e) {
                    FileoutputUtil.outputFileError(FileoutputUtil.PacketEx_Log, e);
                    FileoutputUtil.log(FileoutputUtil.PacketEx_Log, "Packet: " + head + "\n" + slea.toString(true));
                }
                return;
            }
        }
        if (ServerConstants.ShowRecvP) {
            final StringBuilder sb = new StringBuilder("Received data : (未處理)\n");
            sb.append(HexTool.toString((byte[]) msg)).append("\n").append(HexTool.toStringFromAscii((byte[]) msg));
            System.out.println(sb.toString());
        }
    }

    public static void handlePacket(final RecvPacketOpcode header, final LittleEndianAccessor slea, final MapleClient c, final boolean cs) throws Exception {
        switch (header) {
            case PONG:
                c.pongReceived();
                break;
            /*            case STRANGE_DATA:
                // Does nothing for now, HackShield's heartbeat
                break;*/
            case LOGIN_PASSWORD:
                CharLoginHandler.login(slea, c);
                break;
            case SET_GENDER:
                CharLoginHandler.SetGenderRequest(slea, c);
                break;
            case SERVERLIST_REQUEST:
                CharLoginHandler.ServerListRequest(c);
                break;
            case SERVERSTATUS_REQUEST:
                CharLoginHandler.ServerStatusRequest(c);
                break;
            case CHARLIST_REQUEST:
                CharLoginHandler.CharlistRequest(slea, c);
                break;
            case CHECK_CHAR_NAME:
                CharLoginHandler.CheckCharName(slea.readMapleAsciiString(), c);
                break;
            case CREATE_CHAR:
                CharLoginHandler.CreateChar(slea, c);
                break;
            case CREATE_ULTIMATE:
                CharLoginHandler.CreateUltimate(slea, c);
                break;
            case DELETE_CHAR:
                CharLoginHandler.DeleteChar(slea, c);
                break;
            /*            case VIEW_ALL_CHAR:
                CharLoginHandler.ViewChar(slea, c);
                break;
            case PICK_ALL_CHAR:
                CharLoginHandler.Character_WithoutSecondPassword(slea, c, false, true);
                break;
            case CHAR_SELECT_NO_PIC:
                CharLoginHandler.Character_WithoutSecondPassword(slea, c, false, false);
                break;
            case VIEW_REGISTER_PIC:
                CharLoginHandler.Character_WithoutSecondPassword(slea, c, true, true);
                break;*/
            case CHAR_SELECT:
                CharLoginHandler.Character_WithoutSecondPassword(slea, c, true, false);
                break;
            /*            case VIEW_SELECT_PIC:
                CharLoginHandler.Character_WithSecondPassword(slea, c, true);
                break;
            case AUTH_SECOND_PASSWORD:
                CharLoginHandler.Character_WithSecondPassword(slea, c, false);
                break;
            case RSA_KEY: // Fix this somehow
                c.announce(LoginPacket.getLoginAUTH());
                c.announce(LoginPacket.StrangeDATA());
                break;*/
            // END OF LOGIN SERVER
            case CHANGE_CHANNEL:
                InterServerHandler.ChangeChannel(slea, c, c.getPlayer());
                break;
            case PLAYER_LOGGEDIN:
                final int playerid = slea.readInt();
                if (cs) {
                    CashShopOperation.EnterCS(playerid, c);
                } else {
                    InterServerHandler.Loggedin(playerid, c);
                }
                break;
            case ENTER_CASH_SHOP:
                InterServerHandler.EnterCS(c, c.getPlayer());
                break;
            /*            case ENTER_MTS:
                InterServerHandler.EnterCS(c, c.getPlayer(), true);
                break;*/
            case MOVE_PLAYER:
                PlayerHandler.MovePlayer(slea, c, c.getPlayer());
                break;
            case CHAR_INFO_REQUEST:
                c.getPlayer().updateTick(slea.readInt());
                PlayerHandler.CharInfoRequest(slea.readInt(), c, c.getPlayer());
                break;
            case CLOSE_RANGE_ATTACK:
                PlayerHandler.closeRangeAttack(slea, c, c.getPlayer(), false);
                break;
            case RANGED_ATTACK:
                PlayerHandler.rangedAttack(slea, c, c.getPlayer());
                break;
            case MAGIC_ATTACK:
                PlayerHandler.MagicDamage(slea, c, c.getPlayer());
                break;
            case SPECIAL_MOVE:
                PlayerHandler.SpecialMove(slea, c, c.getPlayer());
                break;
            case PASSIVE_ENERGY:
                PlayerHandler.closeRangeAttack(slea, c, c.getPlayer(), true);
                break;
            case GET_BOOK_INFO:
                PlayersHandler.MonsterBookInfoRequest(slea, c, c.getPlayer());
                break;
            case CHANGE_SET:
                PlayersHandler.ChangeSet(slea, c, c.getPlayer());
                break;
            case CRAFT_DONE:
                ItemMakerHandler.CraftComplete(slea, c, c.getPlayer());
                break;
            case CRAFT_MAKE:
                ItemMakerHandler.CraftMake(slea, c, c.getPlayer());
                break;
            case CRAFT_EFFECT:
                ItemMakerHandler.CraftEffect(slea, c, c.getPlayer());
                break;
            case START_HARVEST:
                ItemMakerHandler.StartHarvest(slea, c, c.getPlayer());
                break;
            case STOP_HARVEST:
                ItemMakerHandler.StopHarvest(slea, c, c.getPlayer());
                break;
            case MAKE_EXTRACTOR:
                ItemMakerHandler.MakeExtractor(slea, c, c.getPlayer());
                break;
            case USE_BAG:
                ItemMakerHandler.UseBag(slea, c, c.getPlayer());
                break;
            case MOVE_ANDROID:
                PlayerHandler.MoveAndroid(slea, c, c.getPlayer());
                break;
            case FACE_EXPRESSION:
                PlayerHandler.ChangeEmotion(slea.readInt(), c.getPlayer());
                break;
            case FACE_ANDROID:
                PlayerHandler.ChangeAndroidEmotion(slea.readInt(), c.getPlayer());
                break;
            case TAKE_DAMAGE:
                PlayerHandler.TakeDamage(slea, c, c.getPlayer());
                break;
            case HEAL_OVER_TIME:
                PlayerHandler.Heal(slea, c.getPlayer());
                break;
            case CANCEL_BUFF:
                PlayerHandler.CancelBuffHandler(slea.readInt(), c.getPlayer());
                break;
            case MECH_CANCEL:
                PlayerHandler.CancelMech(slea, c.getPlayer());
                break;
            case CANCEL_ITEM_EFFECT:
                PlayerHandler.CancelItemEffect(slea.readInt(), c.getPlayer());
                break;
            case USE_CHAIR:
                PlayerHandler.UseChair(slea.readInt(), c, c.getPlayer());
                break;
            case CANCEL_CHAIR:
                PlayerHandler.CancelChair(slea.readShort(), c, c.getPlayer());
                break;
            case WHEEL_OF_FORTUNE:
                break; //whatever
            case USE_ITEMEFFECT:
                PlayerHandler.UseItemEffect(slea.readInt(), c, c.getPlayer());
                break;
            case SKILL_EFFECT:
                PlayerHandler.SkillEffect(slea, c.getPlayer());
                break;
            case QUICK_SLOT:
                PlayerHandler.QuickSlot(slea, c.getPlayer());
                break;
            case MESO_DROP:
                c.getPlayer().updateTick(slea.readInt());
                PlayerHandler.DropMeso(slea.readInt(), c.getPlayer());
                break;
            case CHANGE_KEYMAP:
                PlayerHandler.ChangeKeymap(slea, c.getPlayer());
                break;
            case CHANGE_MAP:
                if (cs) {
                    CashShopOperation.LeaveCS(slea, c, c.getPlayer());
                } else {
                    PlayerHandler.ChangeMap(slea, c, c.getPlayer());
                }
                break;
            case CHANGE_MAP_SPECIAL:
                slea.skip(1);
                PlayerHandler.ChangeMapSpecial(slea.readMapleAsciiString(), c, c.getPlayer());
                break;
            case USE_INNER_PORTAL:
                slea.skip(1);
                PlayerHandler.InnerPortal(slea, c, c.getPlayer());
                break;
            case TROCK_ADD_MAP:
                PlayerHandler.TrockAddMap(slea, c, c.getPlayer());
                break;
            case ARAN_COMBO:
                PlayerHandler.AranCombo(c, c.getPlayer(), 1);
                break;
            case SKILL_MACRO:
                PlayerHandler.ChangeSkillMacro(slea, c.getPlayer());
                break;
            case GIVE_FAME:
                PlayersHandler.GiveFame(slea, c, c.getPlayer());
                break;
            case TRANSFORM_PLAYER:
                PlayersHandler.TransformPlayer(slea, c, c.getPlayer());
                break;
            case NOTE_ACTION:
                PlayersHandler.Note(slea, c.getPlayer());
                break;
            case USE_DOOR:
                PlayersHandler.UseDoor(slea, c.getPlayer());
                break;
            case USE_MECH_DOOR:
                PlayersHandler.UseMechDoor(slea, c.getPlayer());
                break;
            case DAMAGE_REACTOR:
                PlayersHandler.HitReactor(slea, c);
                break;
            case CLICK_REACTOR:
            case TOUCH_REACTOR:
                PlayersHandler.TouchReactor(slea, c);
                break;
            case CLOSE_CHALKBOARD:
                c.getPlayer().setChalkboard(null);
                break;
            case ITEM_SORT:
                InventoryHandler.ItemSort(slea, c);
                break;
            case ITEM_GATHER:
                InventoryHandler.ItemGather(slea, c);
                break;
            case ITEM_MOVE:
                InventoryHandler.ItemMove(slea, c);
                break;
            case MOVE_BAG:
                InventoryHandler.MoveBag(slea, c);
                break;
            case SWITCH_BAG:
                InventoryHandler.SwitchBag(slea, c);
                break;
            case ITEM_MAKER:
                ItemMakerHandler.ItemMaker(slea, c);
                break;
            case ITEM_PICKUP:
                InventoryHandler.Pickup_Player(slea, c, c.getPlayer());
                break;
            case USE_CASH_ITEM:
                InventoryHandler.UseCashItem(slea, c);
                break;
            case USE_ITEM:
                InventoryHandler.UseItem(slea, c, c.getPlayer());
                break;
            case USE_COSMETIC:
                InventoryHandler.UseCosmetic(slea, c, c.getPlayer());
                break;
            case USE_MAGNIFY_GLASS:
                InventoryHandler.UseMagnify(slea, c);
                break;
            case USE_SCRIPTED_NPC_ITEM:
                InventoryHandler.UseScriptedNPCItem(slea, c, c.getPlayer());
                break;
            case USE_RETURN_SCROLL:
                InventoryHandler.UseReturnScroll(slea, c, c.getPlayer());
                break;
            case USE_UPGRADE_SCROLL:
                c.getPlayer().updateTick(slea.readInt());
                InventoryHandler.UseUpgradeScroll(slea.readShort(), slea.readShort(), (short) 0/*slea.readShort()*/, c, c.getPlayer());
                break;
            case USE_FLAG_SCROLL:
            case USE_POTENTIAL_SCROLL:
            case USE_EQUIP_SCROLL:
//                c.getPlayer().updateTick(slea.readInt());
                InventoryHandler.UseUpgradeScroll(slea.readShort(), slea.readShort(), (short) 0, c, c.getPlayer());
                break;
            case USE_SUMMON_BAG:
                InventoryHandler.UseSummonBag(slea, c, c.getPlayer());
                break;
            case USE_TREASUER_CHEST:
                InventoryHandler.UseTreasureChest(slea, c, c.getPlayer());
                break;
            case USE_SKILL_BOOK:
                c.getPlayer().updateTick(slea.readInt());
                InventoryHandler.UseSkillBook((byte) slea.readShort(), slea.readInt(), c, c.getPlayer());
                break;
            case USE_CATCH_ITEM:
                InventoryHandler.UseCatchItem(slea, c, c.getPlayer());
                break;
            case USE_MOUNT_FOOD:
                InventoryHandler.UseMountFood(slea, c, c.getPlayer());
                break;
            case REWARD_ITEM:
                InventoryHandler.UseRewardItem((byte) slea.readShort(), slea.readInt(), c, c.getPlayer());
                break;
            case HYPNOTIZE_DMG:
                MobHandler.HypnotizeDmg(slea, c.getPlayer());
                break;
            case MOB_NODE:
                MobHandler.MobNode(slea, c.getPlayer());
                break;
            case DISPLAY_NODE:
                MobHandler.DisplayNode(slea, c.getPlayer());
                break;
            case MOVE_LIFE:
                MobHandler.MoveMonster(slea, c, c.getPlayer());
                break;
            case AUTO_AGGRO:
                MobHandler.AutoAggro(slea.readInt(), c.getPlayer());
                break;
            case FRIENDLY_DAMAGE:
                MobHandler.FriendlyDamage(slea, c.getPlayer());
                break;
            case REISSUE_MEDAL:
                PlayerHandler.ReIssueMedal(slea, c, c.getPlayer());
                break;
            case MONSTER_BOMB:
                MobHandler.MonsterBomb(slea.readInt(), c.getPlayer());
                break;
            case MOB_BOMB:
                MobHandler.MobBomb(slea, c.getPlayer());
                break;
            case NPC_SHOP:
                NPCHandler.NPCShop(slea, c, c.getPlayer());
                break;
            case NPC_TALK:
                NPCHandler.NPCTalk(slea, c, c.getPlayer());
                break;
            case NPC_TALK_MORE:
                NPCHandler.NPCMoreTalk(slea, c);
                break;
            case NPC_ACTION:
                NPCHandler.NPCAnimation(slea, c);
                break;
            case QUEST_ACTION:
                NPCHandler.QuestAction(slea, c, c.getPlayer());
                break;
            case STORAGE:
                NPCHandler.Storage(slea, c, c.getPlayer());
                break;
            case GENERAL_CHAT:
                if (c.getPlayer() != null && c.getPlayer().getMap() != null) {
//                    c.getPlayer().updateTick(slea.readInt());
                    ChatHandler.GeneralChat(slea.readMapleAsciiString()/*, (byte) 1*/, c, c.getPlayer());
                }
                break;
            case PARTYCHAT:
                ChatHandler.Others(slea, c, c.getPlayer());
                break;
            case WHISPER:
                ChatHandler.Whisper_Find(slea, c);
                break;
            case MESSENGER:
                ChatHandler.Messenger(slea, c);
                break;
            case AUTO_ASSIGN_AP:
                StatsHandling.AutoAssignAP(slea, c, c.getPlayer());
                break;
            case DISTRIBUTE_AP:
                StatsHandling.DistributeAP(slea, c, c.getPlayer());
                break;
            case DISTRIBUTE_SP:
                c.getPlayer().updateTick(slea.readInt());
                StatsHandling.DistributeSP(slea.readInt(), c, c.getPlayer());
                break;
            case PLAYER_INTERACTION:
                PlayerInteractionHandler.PlayerInteraction(slea, c, c.getPlayer());
                break;
            case GUILD_OPERATION:
                GuildHandler.Guild(slea, c);
                break;
            case DENY_GUILD_REQUEST:
                slea.skip(1);
                GuildHandler.DenyGuildRequest(slea.readMapleAsciiString(), c);
                break;
            case ALLIANCE_OPERATION:
                AllianceHandler.HandleAlliance(slea, c, false);
                break;
            case DENY_ALLIANCE_REQUEST:
                AllianceHandler.HandleAlliance(slea, c, true);
                break;
            case PUBLIC_NPC:
                NPCHandler.OpenPublicNpc(slea, c);
                break;
            case BBS_OPERATION:
                BBSHandler.BBSOperation(slea, c);
                break;
            case PARTY_OPERATION:
                PartyHandler.PartyOperation(slea, c);
                break;
            case DENY_PARTY_REQUEST:
                PartyHandler.DenyPartyRequest(slea, c);
                break;
            case ALLOW_PARTY_INVITE:
                PartyHandler.AllowPartyInvite(slea, c);
                break;
            case BUDDYLIST_MODIFY:
                BuddyListHandler.BuddyOperation(slea, c);
                break;
            case CYGNUS_SUMMON:
                UserInterfaceHandler.CygnusSummon_NPCRequest(c);
                break;
            case SHIP_OBJECT:
                UserInterfaceHandler.ShipObjectRequest(slea.readInt(), c);
                break;
            case BUY_CS_ITEM:
                CashShopOperation.BuyCashItem(slea, c, c.getPlayer());
                break;
            case COUPON_CODE:
                //FileoutputUtil.log(FileoutputUtil.PacketEx_Log, "Coupon : \n" + slea.toString(true));
                //System.out.println(slea.toString());
                CashShopOperation.CouponCode(slea.readMapleAsciiString(), c);
                CashShopOperation.CouponCode(slea.readMapleAsciiString(), c);
                CashShopOperation.doCSPackets(c);
                break;
            case CS_UPDATE:
                CashShopOperation.CSUpdate(c);
                break;
            case TOUCHING_MTS:
                MTSOperation.MTSUpdate(MTSStorage.getInstance().getCart(c.getPlayer().getId()), c);
                break;
            case MTS_TAB:
                MTSOperation.MTSOperation(slea, c);
                break;
            case USE_POT:
                ItemMakerHandler.UsePot(slea, c);
                break;
            case CLEAR_POT:
                ItemMakerHandler.ClearPot(slea, c);
                break;
            case FEED_POT:
                ItemMakerHandler.FeedPot(slea, c);
                break;
            case CURE_POT:
                ItemMakerHandler.CurePot(slea, c);
                break;
            case DAMAGE_SUMMON:
                SummonHandler.DamageSummon(slea, c.getPlayer());
                break;
            case MOVE_SUMMON:
                SummonHandler.MoveSummon(slea, c.getPlayer());
                break;
            case SUMMON_ATTACK:
                SummonHandler.SummonAttack(slea, c, c.getPlayer());
                break;
            case MOVE_DRAGON:
                SummonHandler.MoveDragon(slea, c.getPlayer());
                break;
            case SUB_SUMMON:
                SummonHandler.SubSummon(slea, c.getPlayer());
                break;
            case REMOVE_SUMMON:
                SummonHandler.RemoveSummon(slea, c);
                break;
            case SPAWN_PET:
                PetHandler.SpawnPet(slea, c, c.getPlayer());
                break;
            case MOVE_PET:
                PetHandler.MovePet(slea, c.getPlayer());
                break;
            case PET_CHAT:
                //System.out.println("Pet chat: " + slea.toString());
                if (slea.available() < 12) {
                    break;
                }
                final int petid = 0/*GameConstants.GMS ? c.getPlayer().getPetIndex((int) slea.readLong()) : slea.readInt()*/;
//                c.getPlayer().updateTick(slea.readInt());
                PetHandler.PetChat(petid, slea.readShort(), slea.readMapleAsciiString(), c.getPlayer());
                break;
            case PET_COMMAND:
                /*                MaplePet pet = null;
                if (GameConstants.GMS) {
                    pet = c.getPlayer().getPet(c.getPlayer().getPetIndex((int) slea.readLong()));
                } else {
                    pet = c.getPlayer().getPet((byte) slea.readInt());
                }*/
                MaplePet pet = c.getPlayer().getPet(0);
                if (pet == null) {
                    return;
                }
                slea.readByte(); //always 0?
                PetHandler.PetCommand(pet, PetDataFactory.getPetCommand(pet.getPetItemId(), slea.readByte()), c, c.getPlayer());
                break;
            case PET_FOOD:
                PetHandler.PetFood(slea, c, c.getPlayer());
                break;
            case PET_LOOT:
                InventoryHandler.Pickup_Pet(slea, c, c.getPlayer());
                break;
            case PET_AUTO_POT:
                PetHandler.Pet_AutoPotion(slea, c, c.getPlayer());
                break;
            case MONSTER_CARNIVAL:
                MonsterCarnivalHandler.MonsterCarnival(slea, c);
                break;
            case DUEY_ACTION:
                DueyHandler.DueyOperation(slea, c);
                break;
            case USE_HIRED_MERCHANT:
                HiredMerchantHandler.UseHiredMerchant(c, true);
                break;
            case MERCH_ITEM_STORE:
                HiredMerchantHandler.MerchantItemStore(slea, c);
                break;
            case CANCEL_DEBUFF:
                // Ignore for now
                break;
            case MAPLETV:
                break;
            case LEFT_KNOCK_BACK:
                PlayerHandler.leftKnockBack(slea, c);
                break;
            case SNOWBALL:
                PlayerHandler.snowBall(slea, c);
                break;
            case COCONUT:
                PlayersHandler.hitCoconut(slea, c);
                break;
            case REPAIR:
                NPCHandler.repair(slea, c);
                break;
            case REPAIR_ALL:
                NPCHandler.repairAll(c);
                break;
            case GAME_POLL:
                UserInterfaceHandler.InGame_Poll(slea, c);
                break;
            case OWL:
                InventoryHandler.Owl(slea, c);
                break;
            case OWL_WARP:
                InventoryHandler.OwlWarp(slea, c);
                break;
            case USE_OWL_MINERVA:
                InventoryHandler.OwlMinerva(slea, c);
                break;
            case RPS_GAME:
                NPCHandler.RPSGame(slea, c);
                break;
            case UPDATE_QUEST:
                NPCHandler.UpdateQuest(slea, c);
                break;
            case USE_ITEM_QUEST:
                NPCHandler.UseItemQuest(slea, c);
                break;
            case FOLLOW_REQUEST:
                PlayersHandler.FollowRequest(slea, c);
                break;
            case AUTO_FOLLOW_REPLY:
            case FOLLOW_REPLY:
                PlayersHandler.FollowReply(slea, c);
                break;
            case RING_ACTION:
                PlayersHandler.RingAction(slea, c);
                break;
            case REQUEST_FAMILY:
                FamilyHandler.RequestFamily(slea, c);
                break;
            case OPEN_FAMILY:
                FamilyHandler.OpenFamily(slea, c);
                break;
            case FAMILY_OPERATION:
                FamilyHandler.FamilyOperation(slea, c);
                break;
            case DELETE_JUNIOR:
                FamilyHandler.DeleteJunior(slea, c);
                break;
            case DELETE_SENIOR:
                FamilyHandler.DeleteSenior(slea, c);
                break;
            case USE_FAMILY:
                FamilyHandler.UseFamily(slea, c);
                break;
            case FAMILY_PRECEPT:
                FamilyHandler.FamilyPrecept(slea, c);
                break;
            case FAMILY_SUMMON:
                FamilyHandler.FamilySummon(slea, c);
                break;
            case ACCEPT_FAMILY:
                FamilyHandler.AcceptFamily(slea, c);
                break;
            case SOLOMON:
                PlayersHandler.Solomon(slea, c);
                break;
            case GACH_EXP:
                PlayersHandler.GachExp(slea, c);
                break;
            case PARTY_SEARCH_START:
                PartyHandler.PartySearchStart(slea, c);
                break;
            case PARTY_SEARCH_STOP:
                PartyHandler.PartySearchStop(slea, c);
                break;
            case USE_TELE_ROCK:
                InventoryHandler.TeleRock(slea, c);
                break;
            case REPORT:
                PlayersHandler.Report(slea, c);
                break;
            case PACHINKO_GAME:
                PachinkoHandler.PachinkoGameAction(slea, c);
                break;
            case PACHINKO_UPDATE:
                PachinkoHandler.PachinkoUpdate(slea, c);
                break;
            default:
                System.out.println("[未處理] Recv [" + header.toString() + "] 數據包");
                break;
        }
    }
}
