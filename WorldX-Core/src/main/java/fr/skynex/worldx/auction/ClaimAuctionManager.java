package fr.skynex.worldx.auction;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.integration.EconomyIntegration;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.scheduler.FoliaScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimAuctionManager {

    private final WorldX plugin;
    private final Map<String, AuctionEntry> activeAuctions = new ConcurrentHashMap<>();

    public ClaimAuctionManager(WorldX plugin) {
        this.plugin = plugin;
        startAuctionChecker();
    }

    public boolean createAuction(Region region, double startingPrice, long durationMs) {
        if (region == null || activeAuctions.containsKey(region.getId())) {
            return false;
        }

        AuctionEntry entry = new AuctionEntry(
                region.getId(),
                startingPrice,
                startingPrice,
                null,
                System.currentTimeMillis() + durationMs
        );
        activeAuctions.put(region.getId(), entry);
        return true;
    }

    public boolean placeBid(Player bidder, String regionId, double amount) {
        AuctionEntry auction = activeAuctions.get(regionId);
        if (auction == null || System.currentTimeMillis() >= auction.endTime) {
            return false;
        }

        if (amount <= auction.currentBid) {
            return false;
        }

        if (EconomyIntegration.setupEconomy()) {
            double balance = EconomyIntegration.getBalance(bidder);
            if (balance < amount) {
                return false;
            }
            if (!EconomyIntegration.withdrawPlayer(bidder, amount)) {
                return false;
            }
            // Refund previous highest bidder
            if (auction.highestBidder != null) {
                OfflinePlayer prev = Bukkit.getOfflinePlayer(auction.highestBidder);
                EconomyIntegration.depositPlayer(prev, auction.currentBid);
            }
        }

        auction.currentBid = amount;
        auction.highestBidder = bidder.getUniqueId();
        return true;
    }

    public AuctionEntry getAuction(String regionId) {
        return activeAuctions.get(regionId);
    }

    public Map<String, AuctionEntry> getActiveAuctions() {
        return Collections.unmodifiableMap(activeAuctions);
    }

    private void startAuctionChecker() {
        FoliaScheduler.runTaskTimer(plugin, () -> {
            if (activeAuctions.isEmpty()) return;

            long now = System.currentTimeMillis();
            List<String> expired = new ArrayList<>();

            for (AuctionEntry auction : activeAuctions.values()) {
                if (now >= auction.endTime) {
                    expired.add(auction.regionId);
                }
            }

            for (String regionId : expired) {
                finalizeAuction(regionId);
            }
        }, 100L, 100L); // Every 5 seconds
    }

    private void finalizeAuction(String regionId) {
        AuctionEntry auction = activeAuctions.remove(regionId);
        if (auction == null) return;

        Region region = plugin.getRegionManager().getRegion(regionId);
        if (region == null) return;

        if (auction.highestBidder != null) {
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.highestBidder);
            region.clearOwners();
            region.addOwner(winner.getUniqueId());
            plugin.getRegionManager().addRegion(region); // Save & sync

            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                    "<green><b>[WorldX Enchères]</b> Le claim <gold>" + regionId + "</gold> a été remporté aux enchères par <yellow>" +
                            (winner.getName() != null ? winner.getName() : winner.getUniqueId()) + "</yellow> pour <gold>" + auction.currentBid + "$</gold> !"
            ));
        } else {
            // No bids placed -> Delete region claim
            plugin.getRegionManager().removeRegion(regionId);
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                    "<red><b>[WorldX Enchères]</b> Aucune offre reçue pour le claim <gold>" + regionId + "</gold>. Le territoire a été libéré !</red>"
            ));
        }
    }

    public static class AuctionEntry {
        public final String regionId;
        public final double startingPrice;
        public double currentBid;
        public UUID highestBidder;
        public final long endTime;

        public AuctionEntry(String regionId, double startingPrice, double currentBid, UUID highestBidder, long endTime) {
            this.regionId = regionId;
            this.startingPrice = startingPrice;
            this.currentBid = currentBid;
            this.highestBidder = highestBidder;
            this.endTime = endTime;
        }
    }
}
