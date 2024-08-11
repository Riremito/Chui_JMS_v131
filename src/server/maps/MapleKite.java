package server.maps;

import java.awt.Point;
import client.MapleCharacter;
import client.MapleClient;
//import net.MaplePacket;
import tools.MaplePacketCreator;

public class MapleKite extends MapleMapObject {

    private MapleCharacter owner;
    private String text;
    private int itemid;

    public MapleKite(MapleCharacter owner, String text, int itemid) {
        this.owner = owner;
        this.text = text;
        this.itemid = itemid;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.KITE;
    }

    public MapleCharacter getOwner() {
        return owner;
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        client.announce(MaplePacketCreator.spawnKite(getObjectId(), itemid, owner.getName(), text, getTruePosition()));
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.announce(MaplePacketCreator.removeKite(getObjectId(), false));
    }
}