package fr.skynex.worldx.api;

import fr.skynex.worldx.region.Flag;
import fr.skynex.worldx.region.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public interface WorldXAPI {

    /**
     * Obtenir la version actuelle de WorldX.
     * @return Chaîne de version.
     */
    String getVersion();

    /**
     * Récupérer une région par son identifiant unique.
     * @param id Identifiant de la région (ex: "spawn").
     * @return La région ou null si elle n'existe pas.
     */
    Region getRegion(String id);

    /**
     * Récupérer l'ensemble des régions enregistrées.
     * @return Map (ID -> Region).
     */
    Map<String, Region> getRegions();

    /**
     * Obtenir la région prioritaire contenant un emplacement donné.
     * @param location Emplacement dans le monde.
     * @return La région prioritaire ou null si hors région.
     */
    Region getHighestPriorityRegion(Location location);

    /**
     * Vérifier si un joueur se trouve physiquement dans une région spécifique.
     * @param player Le joueur à tester.
     * @param regionId Identifiant de la région.
     * @return true si le joueur est dans la région, false sinon.
     */
    boolean isPlayerInRegion(Player player, String regionId);

    /**
     * Obtenir la valeur effective d'un drapeau (flag) pour une région (prend en compte l'héritage parent/enfant).
     * @param region La région ciblée.
     * @param flagName Nom du flag (ex: "pvp", "build", "god-mode").
     * @return La valeur du flag ou null.
     */
    String getEffectiveFlagValue(Region region, String flagName);

    /**
     * Obtenir la valeur effective d'un drapeau (flag) via l'Enum Flag.
     * @param region La région ciblée.
     * @param flag Drapeau ciblé.
     * @return La valeur du flag ou null.
     */
    default String getEffectiveFlagValue(Region region, Flag flag) {
        return flag != null ? getEffectiveFlagValue(region, flag.name()) : null;
    }

    /**
     * Définir ou modifier un drapeau (flag) sur une région.
     * @param region La région à modifier.
     * @param flagName Nom du flag.
     * @param value Valeur ("allow", "deny", ou personnalisée).
     */
    void setFlag(Region region, String flagName, String value);

    /**
     * Définir ou modifier un drapeau (flag) via l'Enum Flag.
     * @param region La région à modifier.
     * @param flag Drapeau ciblé.
     * @param value Valeur ("allow", "deny", ou personnalisée).
     */
    default void setFlag(Region region, Flag flag, String value) {
        if (flag != null) {
            setFlag(region, flag.name(), value);
        }
    }

    /**
     * Vérifier si un flag est autorisé ("allow") sur une région.
     * @param region La région ciblée.
     * @param flag Drapeau ciblé.
     * @param defaultValue Valeur par défaut si non spécifié.
     * @return true si autorisé, false sinon.
     */
    default boolean isFlagAllowed(Region region, Flag flag, boolean defaultValue) {
        if (region == null || flag == null) return defaultValue;
        String val = getEffectiveFlagValue(region, flag);
        if (val == null) return defaultValue;
        return "allow".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val);
    }

    /**
     * Vérifier si un flag est autorisé ("allow") sur une région (valeur par défaut true).
     * @param region La région ciblée.
     * @param flag Drapeau ciblé.
     * @return true si autorisé, false sinon.
     */
    default boolean isFlagAllowed(Region region, Flag flag) {
        return isFlagAllowed(region, flag, true);
    }

    /**
     * Vérifier si une région est actuellement en cours de siège / capture.
     * @param regionId Identifiant de la région.
     * @return true si un siège est actif.
     */
    boolean isUnderSiege(String regionId);

    /**
     * Vérifier si une région est actuellement aux enchères publiques.
     * @param regionId Identifiant de la région.
     * @return true si l'enchère est active.
     */
    boolean isAuctionActive(String regionId);

    /**
     * Obtenir le nombre de claims attribués à un joueur.
     * @param playerUUID UUID du joueur.
     * @return Nombre de territoires possédés.
     */
    int getPlayerClaimCount(UUID playerUUID);
}
