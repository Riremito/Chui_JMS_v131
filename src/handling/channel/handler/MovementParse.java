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

import constants.GameConstants;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import server.maps.AnimatedMapleMapObject;
import server.movement.*;
import tools.FileoutputUtil;
import tools.data.LittleEndianAccessor;

public class MovementParse {

    //1 = player, 2 = mob, 3 = pet, 4 = summon, 5 = dragon
    public static final List<LifeMovementFragment> parseMovement(final LittleEndianAccessor lea, int kind) {
        final List<LifeMovementFragment> res = new ArrayList<LifeMovementFragment>();
        final byte numCommands = lea.readByte();
        for (byte i = 0; i < numCommands; i++) {
            /*final */byte command = lea.readByte();
            switch (command) {
                case 0: // normal move
                case 5: {
                    final short xpos = lea.readShort();
                    final short ypos = lea.readShort();
                    final short xwobble = lea.readShort();
                    final short ywobble = lea.readShort();
                    final short fh = lea.readShort();
                    final byte newstate = lea.readByte();
                    final short duration = lea.readShort();
                    final AbsoluteLifeMovement alm = new AbsoluteLifeMovement(command, new Point(xpos, ypos), duration, newstate, fh);
                    alm.setPixelsPerSecond(new Point(xwobble, ywobble));
                    // log.trace("Move to {},{} command {} wobble {},{} ? {} state {} duration {}", new Object[] { xpos,
                    // xpos, command, xwobble, ywobble, newstate, duration });
                    res.add(alm);
                    break;
                }
                case 1:
                case 2:
                case 6: 
                case 12: {
                    final short xmod = lea.readShort();
                    final short ymod = lea.readShort();
                    final byte newstate = lea.readByte();
                    final short duration = lea.readShort();
                    final RelativeLifeMovement rlm = new RelativeLifeMovement(command, new Point(xmod, ymod), duration, newstate);
                    res.add(rlm);
                    // log.trace("Relative move {},{} state {}, duration {}", new Object[] { xmod, ymod, newstate,
                    // duration });
                    break;
                }
                case 3:
                case 4:
                case 7:
                case 8: // 二段跳
                case 9:
                case 11: {
                    final short xpos = lea.readShort();
                    final short ypos = lea.readShort();
                    final short fh = lea.readShort();
                    final byte newstate = lea.readByte();
                    final short duration = lea.readShort();
                    final ChairMovement cm = new ChairMovement(command, new Point(xpos, ypos), duration, newstate, fh);
                    res.add(cm);
                    break;
                }
		case 10: {
                    res.add(new ChangeEquipSpecialAwesome(command, lea.readByte()));
                    break;
                }
                default:
                    FileoutputUtil.log(FileoutputUtil.Movement_Log, "Kind movement: " + kind + ", Remaining : " + (numCommands - res.size()) + " New type of movement ID : " + command + ", packet : " + lea.toString(true));
                    return null;
            }
        }
        if (numCommands != res.size()) {
//	    System.out.println("error in movement");
            return null; // Probably hack
        }
        return res;
    }

    public static final void updatePosition(final List<LifeMovementFragment> movement, final AnimatedMapleMapObject target, final int yoffset) {
        if (movement == null) {
            return;
        }
        for (final LifeMovementFragment move : movement) {
            if (move instanceof LifeMovement) {
                if (move instanceof AbsoluteLifeMovement) {
                    /*final */Point position = ((LifeMovement) move).getPosition();
                    position.y += yoffset;
                    target.setPosition(/*((LifeMovement) move).getPosition()*/position);
                }
                target.setStance(((LifeMovement) move).getNewstate());
                if (((LifeMovement) move).getType() != 1 && ((LifeMovement) move).getType() != 2 && ((LifeMovement) move).getType() != 6 && ((LifeMovement) move).getType() != 12) {
                    target.setFh(((LifeMovement) move).getFh());
                }
            }
        }
    }
}
