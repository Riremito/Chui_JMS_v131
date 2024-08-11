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

import java.util.Arrays;

import client.inventory.Item;
import client.inventory.ItemFlag;
import constants.GameConstants;
import client.MapleClient;
import client.MapleCharacter;
import client.inventory.MapleInventoryType;
import handling.world.World;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MapleTrade;
import server.maps.FieldLimitType;
import server.shops.HiredMerchant;
import server.shops.IMaplePlayerShop;
import server.shops.MaplePlayerShop;
import server.shops.MaplePlayerShopItem;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.shops.MapleMiniGame;
import tools.MaplePacketCreator;
import tools.packet.PlayerShopPacket;
import tools.data.LittleEndianAccessor;

public class PlayerInteractionHandler {

    private enum Interaction {

        CREATE(GameConstants.GMS ? 6 : 0x00),
        INVITE_TRADE(GameConstants.GMS ? 11 : 0x02),
        DENY_TRADE(GameConstants.GMS ? 12 : 0x03),
        VISIT(GameConstants.GMS ? 9 : 0x04),
        CHAT(GameConstants.GMS ? 14 : 0x06),
        EXIT(GameConstants.GMS ? 18 : 0x0A),
        OPEN(GameConstants.GMS ? 16 : 0x0B),
        SET_ITEMS(GameConstants.GMS ? 0 : 0x0D),
        SET_MESO(GameConstants.GMS ? 1 : 0x0E),
        CONFIRM_TRADE(GameConstants.GMS ? 2 : 0x0F),
        PLAYER_SHOP_ADD_ITEM(GameConstants.GMS ? 40 : 0x13 - 1),
        BUY_ITEM_PLAYER_SHOP(GameConstants.GMS ? 22 : 0x14 - 1),
        ADD_ITEM(GameConstants.GMS ? 23 : 0x15 - 1),
        BUY_ITEM_STORE(GameConstants.GMS ? 24 : 0x16 - 1),
        BUY_ITEM_HIREDMERCHANT(GameConstants.GMS ? 26 : 0x17 - 1),
        REMOVE_ITEM(GameConstants.GMS ? 28 : 0x18 - 1),
        MAINTANCE_ADD_ITEM(0xFF),
        MAINTANCE_OFF(0xFF), //This is misspelled...
        MAINTANCE_ORGANISE(0xFF),
        CLOSE_MERCHANT(0xFF),
        ADMIN_STORE_NAMECHANGE(0xFF),
        VIEW_MERCHANT_VISITOR(0xFF),
        VIEW_MERCHANT_BLACKLIST(0xFF),
        MERCHANT_BLACKLIST_ADD(0xFF),
        MERCHANT_BLACKLIST_REMOVE(0xFF),
        REQUEST_TIE(0x18),
        ANSWER_TIE(0x19),
        GIVE_UP(0x1A),
        REQUEST_REDO(0x1C),
        ANSWER_REDO(0x1D),
        EXIT_AFTER_GAME(0x1E),
        CANCEL_EXIT(0x1F),
        READY(0x20),
        UN_READY(0x21),
        EXPEL(0x22),
        START(0x23),
        SKIP(0x25),
        MOVE_OMOK(0x26),
        SELECT_CARD(0x28);
        public int action;

        private Interaction(int action) {
            this.action = action;
        }

        public static Interaction getByAction(int i) {
            for (Interaction s : Interaction.values()) {
                if (s.action == i) {
                    return s;
                }
            }
            return null;
        }
    }

    public static final void PlayerInteraction(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        //System.out.println(slea.toString());
        final Interaction action = Interaction.getByAction(slea.readByte());
        if (chr == null || action ==  null) {
            return;
        }
        c.getPlayer().setScrolledPosition((short) 0);
        
        switch (action) { // Mode
            case CREATE: {
                if (chr.getPlayerShop() != null || c.getChannelServer().isShutdown() || chr.hasBlockedInventory()) {
                    c.announce(MaplePacketCreator.enableActions());
                    return;
                }
                final byte createType = slea.readByte();
                if (createType == 3) { // trade
                    MapleTrade.startTrade(chr);
                } else if (createType == 1 || createType == 2 || createType == 4 || createType == 5) { // shop
                    //if (createType == 4 && !chr.isIntern()) { //not hired merch... blocked playershop
                    //    c.announce(MaplePacketCreator.enableActions());
                    //    return;
                    //}
/*                    if (chr.getMap().getMapObjectsInRange(chr.getTruePosition(), 20000, Arrays.asList(MapleMapObjectType.SHOP, MapleMapObjectType.HIRED_MERCHANT)).size() != 0 || chr.getMap().getPortalsInRange(chr.getTruePosition(), 20000).size() != 0) {
                        chr.dropMessage(1, "You may not establish a store here.");
                        c.announce(MaplePacketCreator.enableActions());
                        return;
                    } else */if (createType == 1 || createType == 2) {
                        if (FieldLimitType.Minigames.check(chr.getMap().getFieldLimit()) || chr.getMap().allowPersonalShop()) {
                            chr.dropMessage(1, "You may not use minigames here.");
                            c.announce(MaplePacketCreator.enableActions());
                            return;
                        }
                    }
                    final String desc = slea.readMapleAsciiString();
                    String pass = "";
                    if (slea.readByte() > 0) {
                        pass = slea.readMapleAsciiString();
                    }
                    if (createType == 1 || createType == 2) {
                        slea.skip(8);
                        final int piece = slea.readByte();
                        final int itemId = createType == 1 ? (4080000 + piece) : 4080100;
                        if (!chr.haveItem(itemId) || (c.getPlayer().getMapId() >= 910000001 && c.getPlayer().getMapId() <= 910000022)) {
                            return;
                        }
                        MapleMiniGame game = new MapleMiniGame(chr, itemId, desc, pass, createType); //itemid
                        game.setPieceType(piece);
                        chr.setPlayerShop(game);
                        game.setAvailable(true);
                        game.setOpen(true);
                        game.send(c);
                        chr.getMap().addMapObject(game);
                        game.update();
                    } else if (chr.getMap().allowPersonalShop()) {
//                        Item shop = c.getPlayer().getInventory(MapleInventoryType.CASH).getItem((byte) slea.readShort());
                        Item shop = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem((byte) slea.readInt());
                        if (shop == null || shop.getQuantity() <= 0 /*|| shop.getItemId() != slea.readInt() */|| c.getPlayer().getMapId() < 910000001 || c.getPlayer().getMapId() > 910000022) {
                            return;
                        }
                        if (createType == 4) {
                            MaplePlayerShop mps = new MaplePlayerShop(chr, shop.getItemId(), desc);
                            chr.setPlayerShop(mps);
                            chr.getMap().addMapObject(mps);
                            c.announce(PlayerShopPacket.getPlayerStore(chr, true));
                        } else if (HiredMerchantHandler.UseHiredMerchant(chr.getClient(), false)) {
                            final HiredMerchant merch = new HiredMerchant(chr, shop.getItemId(), desc);
                            chr.setPlayerShop(merch);
                            chr.getMap().addMapObject(merch);
                            c.announce(PlayerShopPacket.getHiredMerch(chr, merch, true));
                        }
                    }
                }
                break;
            }
            case INVITE_TRADE: {
                if (chr.getMap() == null) {
                    return;
                }
                MapleCharacter chrr = chr.getMap().getCharacterById(slea.readInt());
                if (chrr == null || c.getChannelServer().isShutdown() || chrr.hasBlockedInventory()) {
                    c.announce(MaplePacketCreator.enableActions());
                    return;
                }
                MapleTrade.inviteTrade(chr, chrr);
                break;
            }
            case DENY_TRADE: {
                MapleTrade.declineTrade(chr);
                break;
            }
            case VISIT: {
                if (c.getChannelServer().isShutdown()) {
                    c.announce(MaplePacketCreator.enableActions());
                    return;
                }
                if (chr.getTrade() != null && chr.getTrade().getPartner() != null && !chr.getTrade().inTrade()) {
                    MapleTrade.visitTrade(chr, chr.getTrade().getPartner().getChr());
                } else if (chr.getMap() != null && chr.getTrade() == null) {
                    final int obid = slea.readInt();
                    MapleMapObject ob = chr.getMap().getMapObject(obid, MapleMapObjectType.HIRED_MERCHANT);
                    if (ob == null) {
                        ob = chr.getMap().getMapObject(obid, MapleMapObjectType.SHOP);
                    }

                    if (ob instanceof IMaplePlayerShop && chr.getPlayerShop() == null) {
                        final IMaplePlayerShop ips = (IMaplePlayerShop) ob;

                        if (ob instanceof HiredMerchant) {
                            final HiredMerchant merchant = (HiredMerchant) ips;
                            if (merchant.isOwner(chr) && merchant.isOpen() && merchant.isAvailable()) {
                                merchant.setOpen(false);
                                merchant.removeAllVisitors((byte) 16, (byte) 0);
                                chr.setPlayerShop(ips);
                                c.announce(PlayerShopPacket.getHiredMerch(chr, merchant, false));
                            } else {
                                if (!merchant.isOpen() || !merchant.isAvailable()) {
                                    chr.dropMessage(1, "This shop is in maintenance, please come by later.");
                                } else {
                                    if (ips.getFreeSlot() == -1) {
                                        chr.dropMessage(1, "This shop has reached it's maximum capacity, please come by later.");
                                    } else if (merchant.isInBlackList(chr.getName())) {
                                        chr.dropMessage(1, "You have been banned from this store.");
                                    } else {
                                        chr.setPlayerShop(ips);
                                        merchant.addVisitor(chr);
                                        c.announce(PlayerShopPacket.getHiredMerch(chr, merchant, false));
                                    }
                                }
                            }
                        } else {
                            if (ips instanceof MaplePlayerShop && ((MaplePlayerShop) ips).isBanned(chr.getName())) {
                                chr.dropMessage(1, "You have been banned from this store.");
                                return;
                            } else {
                                if (ips.getFreeSlot() < 0 || ips.getVisitorSlot(chr) > -1 || !ips.isOpen() || !ips.isAvailable()) {
                                    c.announce(PlayerShopPacket.getMiniGameFull());
                                } else {
                                    if (slea.available() > 0 && slea.readByte() > 0) { //a password has been entered
                                        String pass = slea.readMapleAsciiString();
                                        if (!pass.equals(ips.getPassword())) {
                                            c.getPlayer().dropMessage(1, "The password you entered is incorrect.");
                                            return;
                                        }
                                    } else if (ips.getPassword().length() > 0) {
                                        c.getPlayer().dropMessage(1, "The password you entered is incorrect.");
                                        return;
                                    }
                                    chr.setPlayerShop(ips);
                                    ips.addVisitor(chr);
                                    if (ips instanceof MapleMiniGame) {
                                        ((MapleMiniGame) ips).send(c);
                                    } else {
                                        c.announce(PlayerShopPacket.getPlayerStore(chr, false));
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
            case CHAT: {
//                chr.updateTick(slea.readInt());
                final String message = slea.readMapleAsciiString();
                if (chr.getTrade() != null) {
                    chr.getTrade().chat(message);
                } else if (chr.getPlayerShop() != null) {
                    final IMaplePlayerShop ips = chr.getPlayerShop();
                    ips.broadcastToVisitors(PlayerShopPacket.shopChat(chr.getName() + " : " + message, ips.getVisitorSlot(chr)));
                    if (chr.getClient().isMonitored()) { //Broadcast info even if it was a command.
                        World.Broadcast.broadcastGMMessage(MaplePacketCreator.serverNotice(6, chr.getName() + " said in " + ips.getOwnerName() + " shop : " + message));
                    }
                }
                break;
            }
            case EXIT: {
                if (chr.getTrade() != null) {
                    MapleTrade.cancelTrade(chr.getTrade(), chr.getClient(), chr);
                } else {
                    final IMaplePlayerShop ips = chr.getPlayerShop();
                    if (ips == null) { //should be null anyway for owners of hired merchants (maintenance_off)
                        return;
                    }
                    if (ips.isOwner(chr) && ips.getShopType() != 1) {
                        ips.closeShop(false, ips.isAvailable()); //how to return the items?
                    } else {
                        ips.removeVisitor(chr);
                    }
                    chr.setPlayerShop(null);
                }
                break;
            }
            case OPEN: {
                // c.getPlayer().haveItem(mode, 1, false, true)
                final IMaplePlayerShop shop = chr.getPlayerShop();
                if (shop != null && shop.isOwner(chr) && shop.getShopType() < 3 && !shop.isAvailable()) {
                    if (chr.getMap().allowPersonalShop()) {
                        if (c.getChannelServer().isShutdown()) {
                            chr.dropMessage(1, "The server is about to shut down.");
                            c.announce(MaplePacketCreator.enableActions());
                            shop.closeShop(shop.getShopType() == 1, false);
                            return;
                        }
                        if (shop.getShopType() == 1 && HiredMerchantHandler.UseHiredMerchant(chr.getClient(), false)) {
                            final HiredMerchant merchant = (HiredMerchant) shop;
                            merchant.setStoreid(c.getChannelServer().addMerchant(merchant));
                            merchant.setOpen(true);
                            merchant.setAvailable(true);
                            chr.getMap().broadcastMessage(PlayerShopPacket.spawnHiredMerchant(merchant));
                            chr.setPlayerShop(null);

                        } else if (shop.getShopType() == 2) {
                            shop.setOpen(true);
                            shop.setAvailable(true);
                            shop.update();
                        }
                    } else {
                        c.getSession().close();
                    }
                }

                break;
            }
            case SET_ITEMS: {
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final MapleInventoryType ivType = MapleInventoryType.getByType(slea.readByte());
                final Item item = chr.getInventory(ivType).getItem((byte) slea.readShort());
                final short quantity = slea.readShort();
                final byte targetSlot = slea.readByte();
                if (chr.getTrade() != null && item != null) {
                    if ((quantity <= item.getQuantity() && quantity >= 0) || GameConstants.isThrowingStar(item.getItemId()) || GameConstants.isBullet(item.getItemId())) {
                        chr.getTrade().setItems(c, item, targetSlot, quantity);
                    }
                }
                break;
            }
            case SET_MESO: {
                final MapleTrade trade = chr.getTrade();
                if (trade != null) {
                    trade.setMeso(slea.readInt());
                }
                break;
            }
            case PLAYER_SHOP_ADD_ITEM:
            case ADD_ITEM: {
                final MapleInventoryType type = MapleInventoryType.getByType(slea.readByte());
                final byte slot = (byte) slea.readShort();
                final short bundles = slea.readShort(); // How many in a bundle
                final short perBundle = slea.readShort(); // Price per bundle
                final int price = slea.readInt();
                if (price <= 0 || bundles <= 0 || perBundle <= 0) {
                    return;
                }
                final IMaplePlayerShop shop = chr.getPlayerShop();
                if (shop == null || !shop.isOwner(chr) || shop instanceof MapleMiniGame) {
                    return;
                }
                final Item ivItem = chr.getInventory(type).getItem(slot);
                final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                if (ivItem != null) {
                    long check = bundles * perBundle;
                    if (check > 32767 || check <= 0) { //This is the better way to check.
                        return;
                    }
                    final short bundles_perbundle = (short) (bundles * perBundle);
//                    if (bundles_perbundle < 0) { // int_16 overflow
//                        return;
//                    }
                    if (ivItem.getQuantity() >= bundles_perbundle) {
                        final short flag = ivItem.getFlag();
                        if (ItemFlag.UNTRADEABLE.check(flag) || ItemFlag.LOCK.check(flag)) {
                            c.announce(MaplePacketCreator.enableActions());
                            return;
                        }
                        if (ii.isDropRestricted(ivItem.getItemId()) || ii.isAccountShared(ivItem.getItemId())) {
                            if (!(ItemFlag.KARMA_EQ.check(flag) || ItemFlag.KARMA_USE.check(flag))) {
                                c.announce(MaplePacketCreator.enableActions());
                                return;
                            }
                        }
/*                        if (bundles_perbundle >= 50 && ivItem.getItemId() == 2340000) {
                            c.setMonitored(true); //hack check
                        }*/
                        if (GameConstants.getLowestPrice(ivItem.getItemId()) > price) {
                            c.getPlayer().dropMessage(1, "The lowest you can sell this for is " + GameConstants.getLowestPrice(ivItem.getItemId()));
                            c.announce(MaplePacketCreator.enableActions());
                            return;
                        }
                        if (GameConstants.isThrowingStar(ivItem.getItemId()) || GameConstants.isBullet(ivItem.getItemId())) {
                            // Ignore the bundles
                            final short OriginalQuantity = ivItem.getQuantity();
                            MapleInventoryManipulator.removeFromSlot(c, type, slot, ivItem.getQuantity(), true);
                            final Item sellItem = ivItem.copy();
                            sellItem.setQuantity(OriginalQuantity);
                            shop.addItem(new MaplePlayerShopItem(sellItem, (short) 1, price));
                        } else {
                            MapleInventoryManipulator.removeFromSlot(c, type, slot, bundles_perbundle, true);
                            final Item sellItem = ivItem.copy();
                            sellItem.setQuantity(perBundle);
                            shop.addItem(new MaplePlayerShopItem(sellItem, bundles, price));
                        }
                        c.announce(PlayerShopPacket.shopItemUpdate(shop));
                    }
                }
                break;
            }
            case CONFIRM_TRADE:
            case BUY_ITEM_PLAYER_SHOP:
            case BUY_ITEM_STORE:
            case BUY_ITEM_HIREDMERCHANT: { // Buy and Merchant buy
                if (chr.getTrade() != null) {
                    MapleTrade.completeTrade(chr);
                    break;
                }
                final int item = slea.readByte();
                final short quantity = slea.readShort();
                //slea.skip(4);
                final IMaplePlayerShop shop = chr.getPlayerShop();
                if (shop == null || shop.isOwner(chr) || shop instanceof MapleMiniGame || item >= shop.getItems().size()) {
                    return;
                }
                final MaplePlayerShopItem tobuy = shop.getItems().get(item);
                if (tobuy == null) {
                    return;
                }
                long check = tobuy.bundles * quantity;
                long check2 = tobuy.price * quantity;
                long check3 = tobuy.item.getQuantity() * quantity;
                if (check <= 0 || check2 > 2147483647 || check2 <= 0 || check3 > 32767 || check3 < 0) { //This is the better way to check.
                    return;
                }
                if (tobuy.bundles < quantity || (tobuy.bundles % quantity != 0 && GameConstants.isEquip(tobuy.item.getItemId())) // Buying
                        || chr.getMeso() - (check2) < 0 || chr.getMeso() - (check2) > 2147483647 || shop.getMeso() + (check2) < 0 || shop.getMeso() + (check2) > 2147483647) {
                    return;
                }
/*                if (quantity >= 50 && tobuy.item.getItemId() == 2340000) {
                    c.setMonitored(true); //hack check
                }*/
                shop.buy(c, item, quantity);
                shop.broadcastToVisitors(PlayerShopPacket.shopItemUpdate(shop));
                break;
            }
            case REMOVE_ITEM: {
                int slot = slea.readShort(); //0
                final IMaplePlayerShop shop = chr.getPlayerShop();

                if (shop == null || !shop.isOwner(chr) || shop instanceof MapleMiniGame || shop.getItems().size() <= 0 || shop.getItems().size() <= slot || slot < 0) {
                    return;
                }
                final MaplePlayerShopItem item = shop.getItems().get(slot);

                if (item != null) {
                    if (item.bundles > 0) {
                        Item item_get = item.item.copy();
                        long check = item.bundles * item.item.getQuantity();
                        if (check < 0 || check > 32767) {
                            return;
                        }
                        item_get.setQuantity((short) check);
                        if (item_get.getQuantity() >= 50 && item.item.getItemId() == 2340000) {
                            c.setMonitored(true); //hack check
                        }
                        if (MapleInventoryManipulator.checkSpace(c, item_get.getItemId(), item_get.getQuantity(), item_get.getOwner())) {
                            MapleInventoryManipulator.addFromDrop(c, item_get, false);
                            item.bundles = 0;
                            shop.removeFromSlot(slot);
                        }
                    }
                }
                c.announce(PlayerShopPacket.shopItemUpdate(shop));
                break;
            }
            case MAINTANCE_OFF: {
                final IMaplePlayerShop shop = chr.getPlayerShop();
                if (shop != null && shop instanceof HiredMerchant && shop.isOwner(chr) && shop.isAvailable()) {
                    shop.setOpen(true);
                    shop.removeAllVisitors(-1, -1);
                }
                break;
            }
            case MAINTANCE_ORGANISE: {
                final IMaplePlayerShop imps = chr.getPlayerShop();
                if (imps != null && imps.isOwner(chr) && !(imps instanceof MapleMiniGame)) {
                    for (int i = 0; i < imps.getItems().size(); i++) {
                        if (imps.getItems().get(i).bundles == 0) {
                            imps.getItems().remove(i);
                        }
                    }
                    if (chr.getMeso() + imps.getMeso() > 0) {
                        chr.gainMeso(imps.getMeso(), false);
                        imps.setMeso(0);
                    }
                    c.announce(PlayerShopPacket.shopItemUpdate(imps));
                }
                break;
            }
            case CLOSE_MERCHANT: {
                final IMaplePlayerShop merchant = chr.getPlayerShop();
                if (merchant != null && merchant.getShopType() == 1 && merchant.isOwner(chr) && merchant.isAvailable()) {
                    c.announce(MaplePacketCreator.serverNotice(1, "Please visit Fredrick for your items."));
                    c.announce(MaplePacketCreator.enableActions());
                    merchant.removeAllVisitors(-1, -1);
                    chr.setPlayerShop(null);
                    merchant.closeShop(true, true);
                }
                break;
            }
            case ADMIN_STORE_NAMECHANGE: { // Changing store name, only Admin
                // 01 00 00 00
                break;
            }
            case VIEW_MERCHANT_VISITOR: {
                final IMaplePlayerShop merchant = chr.getPlayerShop();
                if (merchant != null && merchant.getShopType() == 1 && merchant.isOwner(chr)) {
                    ((HiredMerchant) merchant).sendVisitor(c);
                }
                break;
            }
            case VIEW_MERCHANT_BLACKLIST: {
                final IMaplePlayerShop merchant = chr.getPlayerShop();
                if (merchant != null && merchant.getShopType() == 1 && merchant.isOwner(chr)) {
                    ((HiredMerchant) merchant).sendBlackList(c);
                }
                break;
            }
            case MERCHANT_BLACKLIST_ADD: {
                final IMaplePlayerShop merchant = chr.getPlayerShop();
                if (merchant != null && merchant.getShopType() == 1 && merchant.isOwner(chr)) {
                    ((HiredMerchant) merchant).addBlackList(slea.readMapleAsciiString());
                }
                break;
            }
            case MERCHANT_BLACKLIST_REMOVE: {
                final IMaplePlayerShop merchant = chr.getPlayerShop();
                if (merchant != null && merchant.getShopType() == 1 && merchant.isOwner(chr)) {
                    ((HiredMerchant) merchant).removeBlackList(slea.readMapleAsciiString());
                }
                break;
            }
            case GIVE_UP: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    game.broadcastToVisitors(PlayerShopPacket.getMiniGameResult(game, 0, game.getVisitorSlot(chr)));
                    game.nextLoser();
                    game.setOpen(true);
                    game.update();
                    game.checkExitAfterGame();
                }
                break;
            }
            case EXPEL: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    if (!((MapleMiniGame) ips).isOpen()) {
                        break;
                    }
                    ips.removeAllVisitors(3, 1); //no msg
                }
                break;
            }
            case READY:
            case UN_READY: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (!game.isOwner(chr) && game.isOpen()) {
                        game.setReady(game.getVisitorSlot(chr));
                        game.broadcastToVisitors(PlayerShopPacket.getMiniGameReady(game.isReady(game.getVisitorSlot(chr))));
                    }
                }
                break;
            }
            case START: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOwner(chr) && game.isOpen()) {
                        for (int i = 1; i < ips.getSize(); i++) {
                            if (!game.isReady(i)) {
                                return;
                            }
                        }
                        game.setGameType();
                        game.shuffleList();
                        if (game.getGameType() == 1) {
                            game.broadcastToVisitors(PlayerShopPacket.getMiniGameStart(game.getLoser()));
                        } else {
                            game.broadcastToVisitors(PlayerShopPacket.getMatchCardStart(game, game.getLoser()));
                        }
                        game.setOpen(false);
                        game.update();
                    }
                }
                break;
            }
            case REQUEST_TIE: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    if (game.isOwner(chr)) {
                        game.broadcastToVisitors(PlayerShopPacket.getMiniGameRequestTie(), false);
                    } else {
                        game.getMCOwner().getClient().announce(PlayerShopPacket.getMiniGameRequestTie());
                    }
                    game.setRequestedTie(game.getVisitorSlot(chr));
                }
                break;
            }
            case ANSWER_TIE: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    if (game.getRequestedTie() > -1 && game.getRequestedTie() != game.getVisitorSlot(chr)) {
                        if (slea.readByte() > 0) {
                            game.broadcastToVisitors(PlayerShopPacket.getMiniGameResult(game, 1, game.getRequestedTie()));
                            game.nextLoser();
                            game.setOpen(true);
                            game.update();
                            game.checkExitAfterGame();
                        } else {
                            game.broadcastToVisitors(PlayerShopPacket.getMiniGameDenyTie());
                        }
                        game.setRequestedTie(-1);
                    }
                }
                break;
            }
            case SKIP: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    if (game.getLoser() != ips.getVisitorSlot(chr)) {
                        ips.broadcastToVisitors(PlayerShopPacket.shopChat("Turn could not be skipped by " + chr.getName() + ". Loser: " + game.getLoser() + " Visitor: " + ips.getVisitorSlot(chr), ips.getVisitorSlot(chr)));
                        return;
                    }
                    ips.broadcastToVisitors(PlayerShopPacket.getMiniGameSkip(ips.getVisitorSlot(chr)));
                    game.nextLoser();
                }
                break;
            }
            case MOVE_OMOK: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    if (game.getLoser() != game.getVisitorSlot(chr)) {
                        game.broadcastToVisitors(PlayerShopPacket.shopChat("Omok could not be placed by " + chr.getName() + ". Loser: " + game.getLoser() + " Visitor: " + game.getVisitorSlot(chr), game.getVisitorSlot(chr)));
                        return;
                    }
                    game.setPiece(slea.readInt(), slea.readInt(), slea.readByte(), chr);
                }
                break;
            }
            case SELECT_CARD: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    if (game.getLoser() != game.getVisitorSlot(chr)) {
                        game.broadcastToVisitors(PlayerShopPacket.shopChat("Card could not be placed by " + chr.getName() + ". Loser: " + game.getLoser() + " Visitor: " + game.getVisitorSlot(chr), game.getVisitorSlot(chr)));
                        return;
                    }
                    if (slea.readByte() != game.getTurn()) {
                        game.broadcastToVisitors(PlayerShopPacket.shopChat("Omok could not be placed by " + chr.getName() + ". Loser: " + game.getLoser() + " Visitor: " + game.getVisitorSlot(chr) + " Turn: " + game.getTurn(), game.getVisitorSlot(chr)));
                        return;
                    }
                    final int slot = slea.readByte();
                    final int turn = game.getTurn();
                    final int fs = game.getFirstSlot();
                    if (turn == 1) {
                        game.setFirstSlot(slot);
                        if (game.isOwner(chr)) {
                            game.broadcastToVisitors(PlayerShopPacket.getMatchCardSelect(turn, slot, fs, turn), false);
                        } else {
                            game.getMCOwner().getClient().announce(PlayerShopPacket.getMatchCardSelect(turn, slot, fs, turn));
                        }
                        game.setTurn(0); //2nd turn nao
                        return;
                    } else if (fs > 0 && game.getCardId(fs + 1) == game.getCardId(slot + 1)) {
                        game.broadcastToVisitors(PlayerShopPacket.getMatchCardSelect(turn, slot, fs, game.isOwner(chr) ? 2 : 3));
                        game.setPoints(game.getVisitorSlot(chr)); //correct.. so still same loser. diff turn tho
                    } else {
                        game.broadcastToVisitors(PlayerShopPacket.getMatchCardSelect(turn, slot, fs, game.isOwner(chr) ? 0 : 1));
                        game.nextLoser();//wrong haha

                    }
                    game.setTurn(1);
                    game.setFirstSlot(0);

                }
                break;
            }
            case EXIT_AFTER_GAME:
            case CANCEL_EXIT: {
                final IMaplePlayerShop ips = chr.getPlayerShop();
                if (ips != null && ips instanceof MapleMiniGame) {
                    MapleMiniGame game = (MapleMiniGame) ips;
                    if (game.isOpen()) {
                        break;
                    }
                    game.setExitAfter(chr);
                    game.broadcastToVisitors(PlayerShopPacket.getMiniGameExitAfter(game.isExitAfter(chr)));
                }
                break;
            }
            default: {
                //some idiots try to send huge amounts of data to this (:
                //System.out.println("Unhandled interaction action by " + chr.getName() + " : " + action + ", " + slea.toString());
                //19 (0x13) - 00 OR 01 -> itemid(maple leaf) ? who knows what this is
                break;
            }
        }
    }
}
