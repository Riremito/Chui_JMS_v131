package tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import provider.MapleData;
import provider.MapleDataProvider;
import provider.MapleDataProviderFactory;

/**
 *
 * @author Danny
 */
public class WZStringDumper {

        public static void main(String args[]) throws FileNotFoundException, IOException {
                File stringFile = MapleDataProviderFactory.fileInWZPath("string.wz");
                MapleDataProvider stringProvider = MapleDataProviderFactory.getDataProvider(stringFile);

                MapleData item = stringProvider.getData("Item.img");
                MapleData cash = item.getChildByPath("Cash");
                MapleData consume = item.getChildByPath("Con");
                MapleData eqp = item.getChildByPath("Eqp");
                MapleData etc = item.getChildByPath("Etc");
                MapleData ins = item.getChildByPath("Ins");
                MapleData pet = item.getChildByPath("Pet");
                MapleData map = stringProvider.getData("Map.img");
                MapleData mob = stringProvider.getData("Mob.img");
                MapleData skill = stringProvider.getData("Skill.img");
                MapleData npc = stringProvider.getData("Npc.img");

                String output = "代碼"/*args[0]*/;

                File outputDir = new File(output);
                File cashTxt = new File(output + "\\Cash.txt");
                File useTxt = new File(output + "\\Use.txt");
                File eqpDir = new File(output + "\\Equip");
                File etcTxt = new File(output + "\\Etc.txt");
                File insTxt = new File(output + "\\Setup.txt");
                File petTxt = new File(output + "\\Pet.txt");
                File mapTxt = new File(output + "\\Map.txt");
                File mobTxt = new File(output + "\\Mob.txt");
                File skillTxt = new File(output + "\\Skill.txt");
                File npcTxt = new File(output + "\\NPC.txt");
                outputDir.mkdir();
                cashTxt.createNewFile();
                useTxt.createNewFile();
                eqpDir.mkdir();
                etcTxt.createNewFile();
                insTxt.createNewFile();
                petTxt.createNewFile();
                mapTxt.createNewFile();
                mobTxt.createNewFile();
                skillTxt.createNewFile();
                npcTxt.createNewFile();

                System.out.println("Dumping Cash.img strings...");
                PrintWriter writer = new PrintWriter(new FileOutputStream(cashTxt));
                for (MapleData child : cash.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        MapleData descData = child.getChildByPath("desc");
                        String name = "";
                        String desc = "(no description)";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        if (descData != null) {
                                desc = (String) descData.getData();
                        }
                        writer.println(child.getName() + " - " + name + " - " + desc);
                }
                writer.flush();
                writer.close();
                System.out.println("Cash.img complete.");

                System.out.println("Dumping Consume.img strings...");
                writer = new PrintWriter(new FileOutputStream(useTxt));
                for (MapleData child : consume.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        MapleData descData = child.getChildByPath("desc");
                        String name = "";
                        String desc = "(no description)";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        if (descData != null) {
                                desc = (String) descData.getData();
                        }
                        writer.println(child.getName() + " - " + name + " - " + desc);
                }
                writer.flush();
                writer.close();
                System.out.println("Consume.img complete.");

                System.out.println("Dumping Eqp.img strings...");
                for (MapleData child : eqp.getChildren()) {
                        System.out.println("Dumping " + child.getName() + " strings...");
                        File eqpFile = new File(output + "\\Equip\\" + child.getName() + ".txt");
                        eqpFile.createNewFile();
                        PrintWriter eqpWriter = new PrintWriter(new FileOutputStream(eqpFile));
                        for (MapleData child2 : child.getChildren()) {
                                MapleData nameData = child2.getChildByPath("name");
                                MapleData descData = child2.getChildByPath("desc");
                                String name = "";
                                String desc = "(no description)";
                                if (nameData != null) {
                                        name = (String) nameData.getData();
                                }
                                if (descData != null) {
                                        desc = (String) descData.getData();
                                }
                                eqpWriter.println(child2.getName() + " - " + name + " - " + desc);
                        }
                        eqpWriter.flush();
                        eqpWriter.close();
                        System.out.println(child.getName() + " complete.");
                }
                System.out.println("Eqp.img complete.");

                System.out.println("Dumping Etc.img strings...");
                writer = new PrintWriter(new FileOutputStream(etcTxt));
                for (MapleData child : etc.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        MapleData descData = child.getChildByPath("desc");
                        String name = "";
                        String desc = "(no description)";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        if (descData != null) {
                                desc = (String) descData.getData();
                        }
                        writer.println(child.getName() + " - " + name + " - " + desc);
                }
                writer.flush();
                writer.close();
                System.out.println("Etc.img complete.");

                System.out.println("Dumping Ins.img strings...");
                writer = new PrintWriter(new FileOutputStream(insTxt));
                for (MapleData child : ins.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        MapleData descData = child.getChildByPath("desc");
                        String name = "";
                        String desc = "(no description)";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        if (descData != null) {
                                desc = (String) descData.getData();
                        }
                        writer.println(child.getName() + " - " + name + " - " + desc);
                }
                writer.flush();
                writer.close();
                System.out.println("Ins.img complete.");

                System.out.println("Dumping Pet.img strings...");
                writer = new PrintWriter(new FileOutputStream(petTxt));
                for (MapleData child : pet.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        MapleData descData = child.getChildByPath("desc");
                        String name = "";
                        String desc = "(no description)";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        if (descData != null) {
                                desc = (String) descData.getData();
                        }
                        writer.println(child.getName() + " - " + name + " - " + desc);
                }
                writer.flush();
                writer.close();
                System.out.println("Pet.img complete.");

                System.out.println("Dumping Map.img strings...");
                writer = new PrintWriter(new FileOutputStream(mapTxt));
                for (MapleData child : map.getChildren()) {
                        writer.println(child.getName());
                        writer.println();
                        for (MapleData child2 : child.getChildren()) {
                                MapleData streetData = child2.getChildByPath("streetName");
                                MapleData mapData = child2.getChildByPath("mapName");
                                String streetName = "(no street name)";
                                String mapName = "(no map name)";
                                if (streetData != null) {
                                        streetName = (String) streetData.getData();
                                }
                                if (mapData != null) {
                                        mapName = (String) mapData.getData();
                                }
                                writer.println(child2.getName() + " - " + streetName + " - " + mapName);
                        }
                        writer.println();
                }
                writer.flush();
                writer.close();
                System.out.println("Map.img complete.");

                System.out.println("Dumping Mob.img strings...");
                writer = new PrintWriter(new FileOutputStream(mobTxt));
                for (MapleData child : mob.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        String name = "";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        writer.println(child.getName() + " - " + name);
                }
                writer.flush();
                writer.close();
                System.out.println("Mob.img complete.");
                
                System.out.println("Dumping Skill.img strings...");
                writer = new PrintWriter(new FileOutputStream(skillTxt));
                for (MapleData child : skill.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        MapleData descData = child.getChildByPath("desc");
                        MapleData bookData = child.getChildByPath("bookName");
                        String name = "";
                        String desc = "";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        if (descData != null) {
                                desc = (String) descData.getData();
                        }
                        if (bookData != null) {
                                continue;
                        } else {
                                writer.println(child.getName() + " - " + name + " - " + desc);
                        }
                }
                writer.flush();
                writer.close();
                System.out.println("Skill.img complete.");

                System.out.println("Dumping Npc.img strings...");
                writer = new PrintWriter(new FileOutputStream(npcTxt));
                for (MapleData child : npc.getChildren()) {
                        MapleData nameData = child.getChildByPath("name");
                        String name = "";
                        if (nameData != null) {
                                name = (String) nameData.getData();
                        }
                        writer.println(child.getName() + " - " + name);
                }
                writer.flush();
                writer.close();
                System.out.println("Npc.img complete.");
        }
}