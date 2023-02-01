package me.gabrielfj.multiplebedspawn.listeners;

import me.gabrielfj.multiplebedspawn.MultipleBedSpawn;
import me.gabrielfj.multiplebedspawn.models.BedData;
import me.gabrielfj.multiplebedspawn.models.BedsDataType;
import me.gabrielfj.multiplebedspawn.models.PlayerBedsData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MenuHandler implements Listener {

    static MultipleBedSpawn plugin;

    public MenuHandler(MultipleBedSpawn plugin) {
        this.plugin = plugin;
    }

    public void removePlayerBed(String bedUUID, String uuid){

        Player p = Bukkit.getPlayer(UUID.fromString(uuid));

        // checks if player object exists
        if (p != null) {

            PersistentDataContainer playerData = p.getPersistentDataContainer();
            // checks to see if player has beds
            if (playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
                PlayerBedsData playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
                HashMap<String, BedData> beds = playerBedsData.getPlayerBedData();
                if (beds.containsKey(bedUUID)){
                    playerBedsData.removeBed(bedUUID);
                    if (beds==null){
                        playerData.remove(new NamespacedKey(plugin, "beds"));
                    }else{
                        playerData.set(new NamespacedKey(plugin, "beds"), new BedsDataType(), playerBedsData);
                    }
                }
            }

        }
    }

    public void updateItens(Inventory gui, Player p){

        if (gui.getViewers().toString().length()>2){

            ItemStack itens[] = gui.getContents();
            boolean hasActiveCooldown = false;
            for (ItemStack item : itens){

                if (item!=null && item.hasItemMeta()){

                    ItemMeta item_meta = item.getItemMeta();
                    PersistentDataContainer data = item_meta.getPersistentDataContainer();

                    if (data.has(new NamespacedKey(plugin, "cooldown"), PersistentDataType.LONG) && data.has(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING)){

                        long cooldown = data.get(new NamespacedKey(plugin, "cooldown"), PersistentDataType.LONG);
                        String uuid = data.get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);
                        List<String> lore = item_meta.getLore();

                        if (cooldown>System.currentTimeMillis()){
                            hasActiveCooldown = true;
                            long sec = ( cooldown - System.currentTimeMillis() ) / 1000;
                            String seconds = Long.toString(sec);
                            if (lore.size()>2) {
                                lore.set(
                                        2,
                                        ChatColor.GOLD+""+ChatColor.BOLD+plugin.getMessages("cooldown-text").replace("{1}", seconds)
                                );
                            }else {
                                lore.add(
                                        ChatColor.GOLD+""+ChatColor.BOLD+plugin.getMessages("cooldown-text").replace("{1}", seconds)
                                );
                            }
                        }else{
                            if (lore.size()>2) {
                                lore.remove(2);
                            }
                        }

                        item_meta.setLore(lore);
                        item.setItemMeta(item_meta);
                    }
                }
            }

            if (hasActiveCooldown){
                Bukkit.getScheduler().runTaskLater(plugin, () -> {updateItens(gui, p);}, 10L);
            }

        }

    }

    public void openMenu(Player p){

        // gets how much beds player has to use on for loop and for the if check
        PersistentDataContainer playerData = p.getPersistentDataContainer();
        int playerBedsCount = 0;
        PlayerBedsData playerBedsData = null;
        if (playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
            playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
            if (playerBedsData != null && playerBedsData.getPlayerBedData() != null) {
                HashMap<String, BedData> beds = playerBedsData.getPlayerBedData();
                if (!plugin.getConfig().getBoolean("link-worlds")) {
                    HashMap<String, BedData> bedsT = (HashMap<String, BedData>) beds.clone();
                    beds.forEach((uuid, bed) -> {
                        // clear lists so beds are only from the world that player is in
                        if (!bed.getBedWorld().equalsIgnoreCase(p.getWorld().getName())) {
                            bedsT.remove(uuid);
                        }
                    });
                    beds = bedsT;
                }
                playerBedsCount = beds.size();
            }
        }

        // if the player doesnt have any beds than dont open menu
        if (playerBedsCount>0){

            // sets stuff to player be invul and invis on spawn
            p.setInvulnerable(true);
            p.setInvisible(true);
            p.setCanPickupItems(false);
            p.getPersistentDataContainer().set(new NamespacedKey(plugin, "lastWalkspeed"), PersistentDataType.FLOAT, p.getWalkSpeed());
            p.setWalkSpeed(0);


            // create inventory
            int bedCount = playerBedsCount+1;
            Inventory gui = Bukkit.createInventory(p, 9 * ( (int) Math.ceil( bedCount / (Double) 9.0 ) ), ChatColor.translateAlternateColorCodes('&', plugin.getMessages("menu-title")));

            HashMap<String, BedData> beds = playerBedsData.getPlayerBedData();
            if (!plugin.getConfig().getBoolean("link-worlds")) {
                HashMap<String, BedData> bedsT = (HashMap<String, BedData>) beds.clone();
                beds.forEach((uuid, bed) -> {
                    // clear lists so beds are only from the world that player is in
                    if (!bed.getBedWorld().equalsIgnoreCase(p.getWorld().getName())) {
                        bedsT.remove(uuid);
                    }
                });
                beds = bedsT;
            }
            AtomicBoolean hasCooldown = new AtomicBoolean(false);
            AtomicInteger cont= new AtomicInteger(1);
            beds.forEach((uuid, bed) -> {
                ItemStack item = new ItemStack(bed.getBedMaterial(),1);
                ItemMeta item_meta = item.getItemMeta();
                String bedName = plugin.getMessages("default-bed-name").replace("{1}", cont.toString());
                item_meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', bedName));
                if (bed.getBedName()!=null) {
                    item_meta.setDisplayName(bed.getBedName());
                }
                PersistentDataContainer data = item_meta.getPersistentDataContainer();

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.DARK_PURPLE+bed.getBedWorld().toUpperCase());
                String[] location = bed.getBedCoords().split(":");
                String locText = "X: "+location[0].substring(0, location[0].length() - 2)+
                                " Y: "+location[1].substring(0, location[1].length() - 2)+
                                " Z: "+location[2].substring(0, location[2].length() - 2);
                lore.add(ChatColor.GRAY+locText);

                // checks if has any cooldowns
                if (bed.getBedCooldown()>0L){

                    long cooldown = bed.getBedCooldown();
                    if (cooldown>System.currentTimeMillis()){ // if cooldown isnt expired
                        hasCooldown.set(true);
                        data.set(new NamespacedKey(plugin, "cooldown"), PersistentDataType.LONG, cooldown);
                    }else{
                        bed.setBedCooldown(0L);
                    }

                }

                data.set(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING, uuid);
                data.set(new NamespacedKey(plugin, "location"), PersistentDataType.STRING, bed.getBedCoords());
                data.set(new NamespacedKey(plugin, "world"), PersistentDataType.STRING, bed.getBedWorld());

                item_meta.setLore(lore);
                item.setItemMeta(item_meta);
                gui.addItem(item);
                cont.getAndIncrement();
            });

            if (hasCooldown.get()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {updateItens(gui, p);}, 10L);
            }

            ItemStack item = new ItemStack(Material.GRASS_BLOCK,1);
            ItemMeta item_meta = item.getItemMeta();
            item_meta.setDisplayName(ChatColor.YELLOW+"SPAWN");
            item.setItemMeta(item_meta);
            gui.setItem(9 * ( (int) Math.ceil( bedCount / (Double) 9.0 )) -1, item);

            // I dont know why but if openInventory is not on a scheduler is does not open
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                p.openInventory(gui);
            }, 0L);

        }else{

            setPropPlayer(p);

        }

    }

    public boolean checksIfBedExists(Location locBed, Player p, String bedUUID, String worldString){
        World world = Bukkit.getWorld(worldString);
        Block bed = world.getBlockAt(locBed);
        boolean isBed = false;
        if (bed.getBlockData() instanceof Bed bedPart){
            // since the data is in the head we need to set the Block bed to its head
            if (bedPart.getPart().toString()=="FOOT"){
                bed = (Block) bed.getRelative(bedPart.getFacing());
            }
            isBed = true;
        }

        if (!isBed){

            removePlayerBed(bedUUID, p.getUniqueId().toString());
            return false;

        }else{

            BlockState blockState = bed.getState();
            if (blockState instanceof TileState tileState){
                PersistentDataContainer container = tileState.getPersistentDataContainer();

                if (!container.get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING).equalsIgnoreCase(bedUUID)){
                    removePlayerBed(bedUUID, p.getUniqueId().toString());
                    return false;
                }

            }

        }

        return true;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e){

        Player p = e.getPlayer();
        String world = p.getWorld().getName();
        List<String> denylist = plugin.getConfig().getStringList("denylist");
        List<String> allowlist = plugin.getConfig().getStringList("allowlist");
        boolean passLists = (!denylist.contains(world)) && (allowlist.contains(world) || allowlist.isEmpty());
        if (passLists) {
            PersistentDataContainer playerData = p.getPersistentDataContainer();
            PlayerBedsData playerBedsData;
            HashMap<String, BedData> beds;
            if (playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
                playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
                if (playerBedsData != null && playerBedsData.getPlayerBedData() != null) {
                    beds = playerBedsData.getPlayerBedData();
                    if (!plugin.getConfig().getBoolean("link-worlds")) {
                        HashMap<String, BedData> bedsT = (HashMap<String, BedData>) beds.clone();
                        beds.forEach((uuid, bed) -> {
                            if (!bed.getBedWorld().equalsIgnoreCase(world)) {
                                bedsT.remove(uuid);
                            }
                        });
                        beds = bedsT;
                    }
                    beds.forEach((uuid, bed) -> { // loops all beds to check if they still exist
                        String loc[] = bed.getBedCoords().split(":");
                        Location locBed = new Location(Bukkit.getWorld(bed.getBedWorld()), Double.parseDouble(loc[0]), Double.parseDouble(loc[1]),Double.parseDouble(loc[2]));
                        checksIfBedExists(locBed, p, uuid, bed.getBedWorld());
                    });

                }
            }
            openMenu(p);
        }
    }

    public void setPropPlayer(Player p){

        p.setInvisible(false);
        p.setInvulnerable(false);
        p.setCanPickupItems(true);
        if (p.getPersistentDataContainer().has(new NamespacedKey(plugin, "lastWalkspeed"), PersistentDataType.FLOAT)){
            p.setWalkSpeed(p.getPersistentDataContainer().get(new NamespacedKey(plugin, "lastWalkspeed"), PersistentDataType.FLOAT));
            p.getPersistentDataContainer().remove(new NamespacedKey(plugin, "lastWalkspeed"));
        }else {
            p.setWalkSpeed(0.2F);
        }
        if (p.getWalkSpeed()==0.0){
            p.setWalkSpeed(0.2F);
        }
        p.closeInventory();

    }

    public void teleportPlayer(Player p, PersistentDataContainer data, PersistentDataContainer playerData, PlayerBedsData playerBedsData, String uuid){
        boolean isOkayToTP = true;

        if (data.has(new NamespacedKey(plugin, "cooldown"), PersistentDataType.LONG) && data.has(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING)){

            long cooldown = data.get(new NamespacedKey(plugin, "cooldown"), PersistentDataType.LONG);
            if (cooldown>System.currentTimeMillis()){
                isOkayToTP = false;
            }

        }

        if (isOkayToTP) {
            HashMap<String, BedData> beds = playerBedsData.getPlayerBedData();
            setPropPlayer(p);
            String loc[] = beds.get(uuid).getBedSpawnCoords().split(":");
            World world = Bukkit.getWorld(beds.get(uuid).getBedWorld());
            Location locSpawn = new Location(world, Double.parseDouble(loc[0]), Double.parseDouble(loc[1]),Double.parseDouble(loc[2]));
            beds.get(uuid).setBedCooldown( System.currentTimeMillis() + (plugin.getConfig().getLong("bed-cooldown") * 1000) );
            playerData.set(new NamespacedKey(plugin, "beds"), new BedsDataType(), playerBedsData);
            p.teleport(locSpawn);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e){

        if (e.getView().getTitle().equalsIgnoreCase(plugin.getMessages("menu-title"))){
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (e.getCurrentItem() != null){
                PersistentDataContainer playerData = p.getPersistentDataContainer();
                int playerBedsCount = 0;
                PlayerBedsData playerBedsData = null;
                if (playerData.has(new NamespacedKey(plugin, "beds"), new BedsDataType())) {
                    playerBedsData = playerData.get(new NamespacedKey(plugin, "beds"), new BedsDataType());
                    if (playerBedsData!=null && playerBedsData.getPlayerBedData()!=null){
                        playerBedsCount = playerBedsData.getPlayerBedData().size();
                    }
                }
                double bedCount = playerBedsCount + 1;
                int index = e.getSlot();
                if (e.getCurrentItem().getType().toString().toLowerCase().contains("bed")){

                    ItemMeta item_meta = e.getCurrentItem().getItemMeta();
                    PersistentDataContainer data = item_meta.getPersistentDataContainer();

                    String bedCoord[] = data.get(new NamespacedKey(plugin, "location"), PersistentDataType.STRING).split(":");
                    String world = data.get(new NamespacedKey(plugin, "world"), PersistentDataType.STRING);
                    Location location = new Location(p.getWorld(), Double.parseDouble(bedCoord[0]), Double.parseDouble(bedCoord[1]), Double.parseDouble(bedCoord[2]));
                    String uuid = data.get(new NamespacedKey(plugin, "uuid"), PersistentDataType.STRING);

                    if (checksIfBedExists(location , p, uuid, world)){

                        teleportPlayer(p, data, playerData, playerBedsData, uuid);

                    }else{
                        p.closeInventory();
                    }


                }else if(index==9 * ( (int) Math.ceil( bedCount / (Double) 9.0 ) )-1){
                    setPropPlayer(p);
                }
            }

        }

    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent e){

        if (e.getView().getTitle().equalsIgnoreCase(plugin.getMessages("menu-title"))){

            Player p = (Player) e.getPlayer();
            if (!p.getCanPickupItems()){
                openMenu(p);
            }

        }

    }

}
