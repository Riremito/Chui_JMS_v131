package handling.cashshop.handler;

import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

import constants.GameConstants;
import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.inventory.MapleInventoryType;
import client.inventory.MapleRing;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.Item;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.CharacterTransfer;
import handling.world.World;
import java.util.List;
import server.CashItemFactory;
import server.CashItemInfo;
import server.MTSCart;
import server.MTSStorage;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import tools.FileoutputUtil;
import tools.MaplePacketCreator;
import tools.packet.MTSCSPacket;
import tools.Triple;
import tools.data.LittleEndianAccessor;

public class CashShopOperation {

    public static void LeaveCS(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer() == null || World.Find.findChannel(chr.getId()) != -10 || CashShopServer.getPlayerStorage().getCharacterById(chr.getId()) == null) {
            c.getSession().close();
            return;
        }
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            if (cs.getPlayerStorage().getPendingCharacter(chr.getId()) != null || cs.getPlayerStorage().getCharacterById(chr.getId()) != null) {
                c.getSession().close();
                return;
            }
        }
        if (CashShopServer.getPlayerStorage().getPendingCharacter(chr.getId()) != null) {
            c.getSession().close();
            return;
        }
        final int originalchannel = c.getChannel();
        World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), originalchannel);
        CashShopServer.getPlayerStorage().deregisterPlayer(chr);
        c.updateLoginState(MapleClient.CHANGE_CHANNEL, c.getSessionIPAddress());
        final String s = c.getSessionIPAddress();
        LoginServer.addIPAuth(s.substring(s.indexOf('/') + 1, s.length()));
        c.announce(MaplePacketCreator.getChannelChange(c, Integer.parseInt(ChannelServer.getInstance(originalchannel).getIP().split(":")[1])));
        chr.saveToDB(false, true);
        c.setPlayer(null);
        c.setReceiving(false);
    }

/*    public static void LeaveCS(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
//        CashShopServer.getPlayerStorageMTS().deregisterPlayer(chr);
        CashShopServer.getPlayerStorage().deregisterPlayer(chr);
        c.updateLoginState(MapleClient.CHANGE_CHANNEL, c.getSessionIPAddress());
//        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION, c.getSessionIPAddress());
        try {
            World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), c.getChannel());
        } finally {
	    final String s = c.getSessionIPAddress();
	    LoginServer.addIPAuth(s.substring(s.indexOf('/') + 1, s.length()));
            c.announce(MaplePacketCreator.getChannelChange(c, Integer.parseInt(ChannelServer.getInstance(c.getChannel()).getIP().split(":")[1])));
            chr.saveToDB(false, true);
            c.setPlayer(null);
            c.setReceiving(false);
//            c.getSession().close();
        }
    }*/

    public static void EnterCS(final int playerid, final MapleClient c) {
        if (c.getPlayer() != null || World.Find.findChannel(playerid) != -1 || CashShopServer.getPlayerStorage().getCharacterById(playerid) != null) {
            c.getSession().close();
            return;
        }
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            if (cs.getPlayerStorage().getPendingCharacter(playerid) != null || cs.getPlayerStorage().getCharacterById(playerid) != null) {
                c.getSession().close();
                return;
            }
        }
        CharacterTransfer transfer = CashShopServer.getPlayerStorage().getPendingCharacter(playerid);
        final int originalchannel = c.getChannel();
        if (c.getPlayer() != null || transfer == null || originalchannel == -1 || World.Find.findChannel(playerid) != -1) {
            c.getSession().close();
            return;
        }
        MapleCharacter chr = MapleCharacter.ReconstructChr(transfer, c, false);
        if (chr == null) {
            c.getSession().close();
        }
        c.setPlayer(chr);
        c.setAccID(chr.getAccountID());
        if (!c.CheckIPAddress()) { // Remote hack
            c.getSession().close();
            return;
        }
        final int state = c.getLoginState();
        boolean allowLogin = false;
        if (state == MapleClient.LOGIN_SERVER_TRANSITION || state == MapleClient.CHANGE_CHANNEL) {
            if (!World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()))) {
                allowLogin = true;
            }
        }
        if (!allowLogin) {
            c.setPlayer(null);
            c.getSession().close();
            return;
        }
        c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());
        CashShopServer.getPlayerStorage().registerPlayer(chr);
        c.announce(MTSCSPacket.warpCS(c));
        CSUpdate(c);
    }

    public static void CSUpdate(final MapleClient c) {
        doCSPackets(c);
//        c.announce(MTSCSPacket.getCSAccount(c));
        c.announce(MTSCSPacket.getCSGifts(c));
        c.announce(MTSCSPacket.sendWishList(c.getPlayer(), false));
    }

    public static void CouponCode(final String code, final MapleClient c) {
	if (code.length() <= 0) {
	    return;
	}
	Triple<Boolean, Integer, Integer> info = null;
        try {
            info = MapleCharacterUtil.getNXCodeInfo(code);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (info != null && info.left) {
	    int type = info.mid, item = info.right;
            try {
                MapleCharacterUtil.setNXCodeUsed(c.getPlayer().getName(), code);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            /*
             * Explanation of type!
             * Basically, this makes coupon codes do
             * different things!
             *
             * Type 1: A-Cash,
             * Type 2: Maple Points
             * Type 3: Item.. use SN
             * Type 4: Mesos
             */
            Map<Integer, Item> itemz = new HashMap<Integer, Item>();
            int maplePoints = 0, mesos = 0;
            switch (type) {
                case 1:
                case 2:
                    c.getPlayer().modifyCSPoints(type, item, false);
                    maplePoints = item;
                    break;
                case 3:
                    CashItemInfo itez = CashItemFactory.getInstance().getItem(item);
                    if (itez == null) {
                        c.announce(MTSCSPacket.sendCSFail(0));
                        return;
                    }
                    byte slot = MapleInventoryManipulator.addId(c, itez.getId(), (short) 1, "", "Cash shop: coupon code" + " on " + FileoutputUtil.CurrentReadable_Date());
                    if (slot <= -1) {
                        c.announce(MTSCSPacket.sendCSFail(0));
                        return;
                    } else {
                        itemz.put(item, c.getPlayer().getInventory(GameConstants.getInventoryType(item)).getItem(slot));
                    }
                    break;
                case 4:
                    c.getPlayer().gainMeso(item, false);
                    mesos = item;
                    break;
            }
            c.announce(MTSCSPacket.showCouponRedeemedItem(itemz, mesos, maplePoints, c));
        } else {
            c.announce(MTSCSPacket.sendCSFail(info == null ? 0xA7 : 0xA5)); //A1, 9F
        }
    }

    public static final void BuyCashItem(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        final int action = slea.readByte();
        if (action == 0) {
            slea.skip(2);
            CouponCode(slea.readMapleAsciiString(), c);
        } else if (action == 2) {
//            slea.skip(1);
            final int toCharge = (slea.readByte() + 1);
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            if (item != null && chr.getCSPoints(toCharge) >= item.getPrice()) {
                if (!item.genderEquals(c.getPlayer().getGender())) {
                    c.announce(MTSCSPacket.sendCSFail(0xA6));
                    doCSPackets(c);
                    return;
                } else if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                    c.announce(MTSCSPacket.sendCSFail(0xB1));
                    doCSPackets(c);
                    return;
                }
                for (int i : GameConstants.cashBlock) {
                    if (item.getId() == i) {
                        c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                        doCSPackets(c);
                        return;
                    }
                }
                if (item.getId() / 10000 == 524) {
                    c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                    doCSPackets(c);
                    return;
                }
                chr.modifyCSPoints(toCharge, -item.getPrice(), false);
                Item itemz = chr.getCashInventory().toItem(item);
                if (itemz != null && itemz.getUniqueId() > 0 && itemz.getItemId() == item.getId() && itemz.getQuantity() == item.getCount()) {
                    chr.getCashInventory().addToInventory(itemz);
                    //c.announce(MTSCSPacket.confirmToCSInventory(itemz, c.getAccID(), item.getSN()));
                    c.announce(MTSCSPacket.showBoughtCSItem(itemz, item.getSN(), c.getAccID()));
                } else {
                    c.announce(MTSCSPacket.sendCSFail(0));
                }
            } else {
                c.announce(MTSCSPacket.sendCSFail(0));
            }
        } else if (action == 3/* || action == (GameConstants.GMS ? 34 : 33)*/) { //gift, package
            slea.readMapleAsciiString(); // 第二組密碼
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            String partnerName = slea.readMapleAsciiString();
            String msg = slea.readMapleAsciiString();
            if (item == null || c.getPlayer().getCSPoints(1) < item.getPrice() || msg.length() > 73 || msg.length() < 1) { //dont want packet editors gifting random stuff =P
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            Triple<Integer, Integer, Integer> info = MapleCharacterUtil.getInfoByName(partnerName, c.getPlayer().getWorld());
            if (info == null || info.getLeft().intValue() <= 0 || info.getLeft().intValue() == c.getPlayer().getId() || info.getMid().intValue() == c.getAccID()) {
                c.announce(MTSCSPacket.sendCSFail(0xA2)); //9E v75
                doCSPackets(c);
                return;
            } else if (!item.genderEquals(info.getRight().intValue())) {
                c.announce(MTSCSPacket.sendCSFail(0xA3));
                doCSPackets(c);
                return;
            } else {
                for (int i : GameConstants.cashBlock) {
                    if (item.getId() == i) {
                        c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                        doCSPackets(c);
                        return;
                    }
                }
                c.getPlayer().getCashInventory().gift(info.getLeft().intValue(), c.getPlayer().getName(), msg, item.getSN(), MapleInventoryIdentifier.getInstance());
                c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
                c.announce(MTSCSPacket.sendGift(item.getPrice(), item.getId(), item.getCount(), partnerName));
            }
        } else if (action == 4) { // Wishlist
            chr.clearWishlist();
            if (slea.available() < 40) {
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            int[] wishlist = new int[10];
            for (int i = 0; i < 10; i++) {
                wishlist[i] = slea.readInt();
            }
            chr.setWishlist(wishlist);
            c.announce(MTSCSPacket.sendWishList(chr, true));
        } else if (action == 5) { // Increase inv
//            slea.skip(1);
            final int toCharge = (slea.readByte() + 1);
            final boolean coupon = slea.readByte() > 0;
            if (coupon) {
                final MapleInventoryType type = getInventoryType(slea.readInt());
                if (chr.getCSPoints(toCharge) >= 780 && chr.getInventory(type).getSlotLimit() < 49/*89*/) {
                    chr.modifyCSPoints(toCharge, -780, false);
                    chr.getInventory(type).addSlot((byte) 8);
                    c.announce(MTSCSPacket.increasedInvSlots(type, chr.getInventory(type).getSlotLimit()));
//                    chr.dropMessage(1, "Slots has been increased to " + chr.getInventory(type).getSlotLimit());
                } else {
                    c.announce(MTSCSPacket.sendCSFail(0xA4));
                }
            } else {
                final MapleInventoryType type = MapleInventoryType.getByType(slea.readByte());
                if (chr.getCSPoints(toCharge) >= 390 && chr.getInventory(type).getSlotLimit() < 45/*93*/) {
                    chr.modifyCSPoints(toCharge, 390, false);
                    chr.getInventory(type).addSlot((byte) 4);
                    c.announce(MTSCSPacket.increasedInvSlots(type, chr.getInventory(type).getSlotLimit()));
//                    chr.dropMessage(1, "Slots has been increased to " + chr.getInventory(type).getSlotLimit());
                } else {
                    c.announce(MTSCSPacket.sendCSFail(0xA4));
                }
            }
        } else if (action == 6) { // Increase slot space
//            slea.skip(1);
            final int toCharge = (slea.readByte() + 1);
            final int coupon = slea.readByte() > 0 ? 2 : 1;
            if (chr.getCSPoints(toCharge) >= 390 * coupon && chr.getStorage().getSlots() < (49 - (4 * coupon))) {
                chr.modifyCSPoints(toCharge, -390 * coupon, false);
                chr.getStorage().increaseSlots((byte) (4 * coupon));
                chr.getStorage().saveToDB();
                c.announce(MTSCSPacket.increasedStorageSlots(chr.getStorage().getSlots()));
//                chr.dropMessage(1, "Storage slots increased to: " + chr.getStorage().getSlots());
            } else {
                c.announce(MTSCSPacket.sendCSFail(0xA4));
            }
/*        } else if (action == 8) { //...9 = pendant slot expansion
            slea.skip(1);
            final int toCharge = GameConstants.GMS ? slea.readInt() : 1;
            CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            int slots = c.getCharacterSlots();
            if (item == null || c.getPlayer().getCSPoints(toCharge) < item.getPrice() || slots > 15 || item.getId() != 5430000) {
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            if (c.gainCharacterSlot()) {
                c.getPlayer().modifyCSPoints(toCharge, -item.getPrice(), false);
                chr.dropMessage(1, "Character slots increased to: " + (slots+1));
            } else {
                c.announce(MTSCSPacket.sendCSFail(0));
            }*/
        /*} else if (action == 9) { //...9 = pendant slot expansion
            slea.readByte();
	    final int sn = slea.readInt();
            CashItemInfo item = CashItemFactory.getInstance().getItem(sn);
            int slots = c.getCharacterSlots();
            if (item == null || c.getPlayer().getCSPoints(1) < item.getPrice() || item.getId() / 10000 != 555) {
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            }
            MapleQuestStatus marr = c.getPlayer().getQuestNoAdd(MapleQuest.getInstance(GameConstants.PENDANT_SLOT));
	    if (marr != null && marr.getCustomData() != null && Long.parseLong(marr.getCustomData()) >= System.currentTimeMillis()) {
                c.announce(MTSCSPacket.sendCSFail(0));
	    } else {
		c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.PENDANT_SLOT)).setCustomData(String.valueOf(System.currentTimeMillis() + ((long)item.getPeriod() * 24 * 60 * 60000)));
		c.getPlayer().modifyCSPoints(1, -item.getPrice(), false);
                chr.dropMessage(1, "Additional pendant slot gained.");
            }*/
        } else if (action == 10) { // 購物商城→道具欄位
            //uniqueid, 00 01 01 00, type->position(short)
            Item item = c.getPlayer().getCashInventory().findByCashId((int) slea.readLong());
            if (item != null && item.getQuantity() > 0 && MapleInventoryManipulator.checkSpace(c, item.getItemId(), item.getQuantity(), item.getOwner())) {
                Item item_ = item.copy();
                short pos = MapleInventoryManipulator.addbyItem(c, item_, true);
                if (pos >= 0) {
                    if (item_.getPet() != null) {
                        item_.getPet().setInventoryPosition(pos);
                        c.getPlayer().addPet(item_.getPet());
                    }
                    c.getPlayer().getCashInventory().removeFromInventory(item);
                    c.announce(MTSCSPacket.confirmFromCSInventory(item_, pos));
                } else {
                    c.announce(MTSCSPacket.sendCSFail(0xB1));
                }
            } else {
                c.announce(MTSCSPacket.sendCSFail(0xB1));
            }
        } else if (action == 11) { // 道具欄位→購物商城
            int uniqueid = (int) slea.readLong();
            MapleInventoryType type = MapleInventoryType.getByType(slea.readByte());
            Item item = c.getPlayer().getInventory(type).findByUniqueId(uniqueid);
            if (item != null && item.getQuantity() > 0 && item.getUniqueId() > 0 && c.getPlayer().getCashInventory().getItemsSize() < 100) {
                Item item_ = item.copy();
                c.getPlayer().getInventory(type).removeItem(item.getPosition(), item.getQuantity(), false);
//                MapleInventoryManipulator.removeFromSlot(c, type, item.getPosition(), item.getQuantity(), false);
                if (item_.getPet() != null) {
                    c.getPlayer().removePetCS(item_.getPet());
                }
                item_.setPosition((byte) 0);
                c.getPlayer().getCashInventory().addToInventory(item_);
                c.announce(MTSCSPacket.confirmToCSInventory(item, c.getAccID(), 0));
            } else {
                c.announce(MTSCSPacket.sendCSFail(0xB1));
            }
        } else if (action == 0x19/* || action == 0x1F*/) { //36 = friendship, 30 = crush
            //c.announce(MTSCSPacket.sendCSFail(0));
            slea.readMapleAsciiString(); // 第二組密碼
            final int toCharge = 1;
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            final String partnerName = slea.readMapleAsciiString();
            final String msg = slea.readMapleAsciiString();
            if (item == null || !GameConstants.isEffectRing(item.getId()) || c.getPlayer().getCSPoints(toCharge) < item.getPrice() || msg.length() > 73 || msg.length() < 1) {
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            } else if (!item.genderEquals(c.getPlayer().getGender())) {
                c.announce(MTSCSPacket.sendCSFail(0xA6));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getCashInventory().getItemsSize() >= 100) {
                c.announce(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            for (int i : GameConstants.cashBlock) { //just incase hacker
                if (item.getId() == i) {
                    c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                    doCSPackets(c);
                    return;
                }
            }
            Triple<Integer, Integer, Integer> info = MapleCharacterUtil.getInfoByName(partnerName, c.getPlayer().getWorld());
            if (info == null || info.getLeft().intValue() <= 0 || info.getLeft().intValue() == c.getPlayer().getId()) {
                c.announce(MTSCSPacket.sendCSFail(0xB4)); //9E v75
                doCSPackets(c);
                return;
            } else if (info.getMid().intValue() == c.getAccID()) {
                c.announce(MTSCSPacket.sendCSFail(0xA3)); //9D v75
                doCSPackets(c);
                return;
            } else {
                if (info.getRight().intValue() == c.getPlayer().getGender() && action == 30) {
                    c.announce(MTSCSPacket.sendCSFail(0xA1)); //9B v75
                    doCSPackets(c);
                    return;
                }
                int err = MapleRing.createRing(item.getId(), c.getPlayer(), partnerName, msg, info.getLeft().intValue(), item.getSN());
                if (err != 1) {
                    c.announce(MTSCSPacket.sendCSFail(0)); //9E v75
                    doCSPackets(c);
                    return;
                }
                c.getPlayer().modifyCSPoints(toCharge, -item.getPrice(), false);
                //c.announce(MTSCSPacket.showBoughtCSItem(itemz, item.getSN(), c.getAccID()));
                c.announce(MTSCSPacket.sendGift(item.getPrice(), item.getId(), item.getCount(), partnerName));
            }
        } else if (action == 0x1A) {
//            slea.skip(1);
            final int toCharge = (slea.readByte() + 1);
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            List<Integer> ccc = null;
            if (item != null) {
                ccc = CashItemFactory.getInstance().getPackageItems(item.getId());
            }
            if (item == null || ccc == null || c.getPlayer().getCSPoints(toCharge) < item.getPrice()) {
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            } else if (!item.genderEquals(c.getPlayer().getGender())) {
                c.announce(MTSCSPacket.sendCSFail(0xA6));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getCashInventory().getItemsSize() >= (100 - ccc.size())) {
                c.announce(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            for (int iz : GameConstants.cashBlock) {
                if (item.getId() == iz) {
                    c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                    doCSPackets(c);
                    return;
                }
            }
            Map<Integer, Item> ccz = new HashMap<Integer, Item>();
            for (int i : ccc) {
		final CashItemInfo cii = CashItemFactory.getInstance().getSimpleItem(i);
		if (cii == null) {
		    continue;
		}
                Item itemz = c.getPlayer().getCashInventory().toItem(cii);
                if (itemz == null || itemz.getUniqueId() <= 0) {
                    continue;
                }
                for (int iz : GameConstants.cashBlock) {
                    if (itemz.getItemId() == iz) {
                        continue;
                    }
                }
                ccz.put(i, itemz);
                c.getPlayer().getCashInventory().addToInventory(itemz);
            }
            chr.modifyCSPoints(toCharge, -item.getPrice(), false);
            c.announce(MTSCSPacket.showBoughtCSPackage(ccz, c.getAccID()));
        } else if (action == 0x1C) {
            final CashItemInfo item = CashItemFactory.getInstance().getItem(slea.readInt());
            if (item == null || !MapleItemInformationProvider.getInstance().isQuestItem(item.getId())) {
                c.announce(MTSCSPacket.sendCSFail(0));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getMeso() < item.getPrice()) {
                c.announce(MTSCSPacket.sendCSFail(0xB8));
                doCSPackets(c);
                return;
            } else if (c.getPlayer().getInventory(GameConstants.getInventoryType(item.getId())).getNextFreeSlot() < 0) {
                c.announce(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            for (int iz : GameConstants.cashBlock) {
                if (item.getId() == iz) {
                    c.getPlayer().dropMessage(1, GameConstants.getCashBlockedMsg(item.getId()));
                    doCSPackets(c);
                    return;
                }
            }
            byte pos = MapleInventoryManipulator.addId(c, item.getId(), (short) item.getCount(), null, "Cash shop: quest item" + " on " + FileoutputUtil.CurrentReadable_Date());
            if (pos < 0) {
                c.announce(MTSCSPacket.sendCSFail(0xB1));
                doCSPackets(c);
                return;
            }
            chr.gainMeso(-item.getPrice(), false);
            c.announce(MTSCSPacket.showBoughtCSQuestItem(item.getPrice(), (short) item.getCount(), pos, item.getId()));
        } else {
            c.announce(MTSCSPacket.sendCSFail(0));
        }
        doCSPackets(c);
    }

    private static final MapleInventoryType getInventoryType(final int id) {
        switch (id) {
            case 50200049:
                return MapleInventoryType.EQUIP;
            case 50200050:
                return MapleInventoryType.USE;
            case 50200051:
                return MapleInventoryType.SETUP;
            case 50200052:
                return MapleInventoryType.ETC;
            default:
                return MapleInventoryType.UNDEFINED;
        }
    }

    public static final void doCSPackets(MapleClient c) {
        c.announce(MTSCSPacket.getCSInventory(c));
        c.announce(MTSCSPacket.showNXMapleTokens(c.getPlayer()));
        c.announce(MTSCSPacket.enableCSUse());
        c.getPlayer().getCashInventory().checkExpire(c);
    }
}
