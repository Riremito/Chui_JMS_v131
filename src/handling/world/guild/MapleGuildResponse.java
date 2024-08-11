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
package handling.world.guild;

import tools.MaplePacketCreator;


public enum MapleGuildResponse {
    這個名稱有人使用了(0x1C),
    獲得同意出現問題(0x1F),
    有人不同意(0x24),
    NOT_IN_GUILD(0x25),
    創立時發生問題(0x26),
    ALREADY_IN_GUILD(0x28),
    JOIN_GUILD_FULL(0x29),
    NOT_IN_CHANNEL(0x2A),
    加入的公會不存在(0x2D),
    對方為公會招待拒絕狀態(0x35),
    對方正在處理其他招待中(0x36),
    對方拒絕公會招待(0x37),
    GM不行創公會(0x38),
    公會戰人數不夠6人(0x4A),
    ;
    private int value;

    private MapleGuildResponse(int val) {
        value = val;
    }

    public int getValue() {
        return value;
    }

    public byte[] getPacket() {
        return MaplePacketCreator.genericGuildMessage((byte) value);
    }
}
