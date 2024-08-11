package client.messages.commands;

//import client.MapleInventory;
//import client.MapleInventoryType;
import constants.ServerConstants.PlayerGMRank;
import client.MapleClient;
import scripting.NPCScriptManager;
import server.Randomizer;
import tools.MaplePacketCreator;

/**
 *
 * @author Emilyx3
 */
public class PlayerCommand {

    public static PlayerGMRank getPlayerLevelRequired() {
        return PlayerGMRank.NORMAL;
    }

    public static class E extends CommandExecute {

        public int execute(MapleClient c, String[] splitted) {
//            c.removeClickedNPC();
//            NPCScriptManager.getInstance().dispose(c);
            c.announce(MaplePacketCreator.enableActions());
//            c.announce(MaplePacketCreator.serverNotice(5, "已解除異常狀態..!"));
//            c.announce(MaplePacketCreator.yellowChat("已解除異常狀態..!"));
            return 1;
        }
    }

    public static class 骰子 extends CommandExecute {

        public int execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                c.getPlayer().dropMessage(5, "*骰子 <數字>");
                return 0;
            }
//            final int result = Randomizer.rand(1, Integer.parseInt(splitted[1]));
            final int result = Randomizer.nextInt(Integer.parseInt(splitted[1])) + 1;
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getChatText(c.getPlayer().getId(), "1~" + splitted[1] + "投擲骰子，擲出" + result + "。", true), c.getPlayer().getTruePosition());
            return 1;
        }
    }
}