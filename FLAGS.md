# WorldX Plugin - Flags Documentation

Ce document présente la liste complète des flags configurables pour le plugin WorldX. Ils permettent de personnaliser en détail le comportement et les règles au sein de chaque région.

Tous ces flags sont définis dans l'énumération [Flag.java](file:///c:/Users/killi/IdeaProjects/WorldX/src/main/java/fr/skynex/worldx/region/Flag.java).

---

## 🛡️ 1. Protection & Combat
Ces flags contrôlent la sécurité des joueurs, des blocs, et les interactions de combat. La plupart de ces règles sont appliquées dans [ProtectionListener.java](file:///c:/Users/killi/IdeaProjects/WorldX/src/main/java/fr/skynex/worldx/listener/ProtectionListener.java).

| Flag | Description | Type / Valeurs attendues |
| :--- | :--- | :--- |
| **`BUILD`** | Autorise ou refuse la pose et la destruction de blocs. | `allow` / `deny` |
| **`USE`** | Autorise ou refuse l'interaction avec les blocs fonctionnels (coffres, portes, boutons, leviers). | `allow` / `deny` |
| **`PVP`** | Active ou désactive le combat joueur contre joueur. | `allow` / `deny` |
| **`PROJECTILE`** | Contrôle le tir et l'impact de projectiles (flèches, œufs, boules de neige, etc.). | `allow` / `deny` |
| **`ARMOR_STAND_INTERACT`** | Protège les porte-armures contre les dégâts et le vol d'équipement. | `allow` / `deny` |
| **`GOD_MODE`** | Rend les joueurs complètement insensibles aux dégâts dans la région. | `allow` / `deny` |
| **`HOSTILE_ATTACK`** | Autorise ou refuse les attaques des monstres hostiles envers les joueurs. | `allow` / `deny` |
| **`PASSIVE_ATTACK`** | Autorise ou interdit d'attaquer les animaux passifs ou les NPCs. | `allow` / `deny` |
| **`EXPLOSION`** | Active ou désactive les dégâts d'explosions (Creeper, TNT, etc.). | `allow` / `deny` |
| **`EXPLOSION_BLOCK_DAMAGE`** | Spécifie si les explosions peuvent détruire des blocs. | `allow` / `deny` |
| **`FIRE_IGNITE`** | Autorise ou interdit aux joueurs d'allumer du feu manuellement. | `allow` / `deny` |

---

## 🏃 2. Déplacement & Contrôle d'accès
Ces flags déterminent comment les joueurs peuvent se déplacer dans les régions. Ils sont gérés dans [AdvancedFlagsListener.java](file:///c:/Users/killi/IdeaProjects/WorldX/src/main/java/fr/skynex/worldx/listener/AdvancedFlagsListener.java).

| Flag | Description | Type / Valeurs attendues |
| :--- | :--- | :--- |
| **`ENTRY`** | Autorise ou refuse l'accès d'un joueur à la région (les intrus sont repoussés). | `allow` / `deny` |
| **`EXIT`** | Autorise ou refuse la sortie d'un joueur de la région. | `allow` / `deny` |
| **`ALLOW_FLIGHT`** | Permet ou interdit le vol (mode fly) pour les joueurs dans la région. | `allow` / `deny` |
| **`ALLOW_ELYTRA`** | Autorise ou refuse l'utilisation des Élytres pour planer. | `allow` / `deny` |
| **`DENY_ELYTRA_BOOST`** | Empêche d'utiliser des fusées d'artifice pour se propulser en Élytres. | `allow` / `deny` |
| **`SPEED_BOOST`** | Applique un multiplicateur de vitesse de déplacement aux joueurs. | Nombre décimal (ex: `1.5`) |
| **`GRAVITY_MODIFIER`** | Modifie la gravité des joueurs dans la région. | Nombre décimal |
| **`ENDERPEARL`** | Autorise ou interdit la téléportation par perle de l'Ender. | `allow` / `deny` |
| **`MAX_PLAYERS`** | Définit la capacité maximale de joueurs autorisés dans la région. | Nombre entier |

---

## 🧪 3. Effets Périodiques & Boosts
Ces flags appliquent des modifications continues aux joueurs et sont traités via [RegionEffectsTask.java](file:///c:/Users/killi/IdeaProjects/WorldX/src/main/java/fr/skynex/worldx/task/RegionEffectsTask.java).

| Flag | Description | Type / Valeurs attendues |
| :--- | :--- | :--- |
| **`heal-delay`** | Soigne périodiquement les joueurs. | Délai en secondes (ex: `5`) |
| **`feed-delay`** | Nourrit périodiquement les joueurs (restaure la faim). | Délai en secondes |
| **`damage-on-entry`** | Inflige des dégâts réguliers aux joueurs dans la région. | Dégâts numériques par seconde |
| **`potion-effects`** | Applique des effets de potion permanents aux joueurs dans la région. | Liste d'effets (ex: `SPEED:1,INCREASE_DAMAGE:0`) |
| **`xp-multiplier`** | Multiplie les gains d'expérience (XP). | Nombre décimal (ex: `2.0`) |
| **`money-multiplier`** | Multiplie les gains d'argent dans la région. | Nombre décimal |
| **`money-loot-on-kill`** | Donne une quantité d'argent lors de l'élimination de monstres. | Nombre |
| **`absorption-shield`** | Offre des cœurs d'absorption temporaires lors de l'entrée. | Nombre de cœurs |

---

## 🌍 4. Environnement & Blocs
Ces flags modifient les règles de physique et de comportement du monde Minecraft dans la région.

| Flag | Description | Type / Valeurs attendues |
| :--- | :--- | :--- |
| **`FIRE_SPREAD`** | Autorise ou empêche la propagation du feu. | `allow` / `deny` |
| **`BLOCK_BURN`** | Empêche les blocs de brûler et d'être détruits par le feu. | `allow` / `deny` |
| **`LEAF_DECAY`** | Active ou désactive la décomposition naturelle des feuilles d'arbres. | `allow` / `deny` |
| **`ICE_MELT`** | Empêche la glace et la neige de fondre sous l'effet de la lumière. | `allow` / `deny` |
| **`LIQUID_FLOW`** | Active ou désactive l'écoulement de l'eau et de la lave. | `allow` / `deny` |
| **`PISTON_INTERACT`** | Empêche les pistons de pousser ou tirer des blocs à travers les frontières de la région. | `allow` / `deny` |
| **`BLOCK_GROWTH`** | Active ou désactive la croissance naturelle des plantes et cultures. | `allow` / `deny` |
| **`SNOW_FALL`** | Active ou désactive l'accumulation de neige et la formation de glace. | `allow` / `deny` |
| **`FARMLAND_DEHYDRATION`** | Empêche la terre labourée de se dessécher. | `allow` / `deny` |
| **`CROP_TRAMPLE`** | Empêche les joueurs ou entités de piétiner et détruire les cultures. | `allow` / `deny` |
| **`AUTO_REPLANT`** | Replante automatiquement les cultures matures récoltées. | `allow` / `deny` |

---

## 👥 5. Utilitaires & Social

| Flag | Description | Type / Valeurs attendues |
| :--- | :--- | :--- |
| **`KEEP_INVENTORY`** | Permet aux joueurs de garder leur inventaire après leur mort. | `allow` / `deny` |
| **`KEEP_XP`** | Permet aux joueurs de garder leur expérience après leur mort. | `allow` / `deny` |
| **`HUNGER`** | Active ou désactive la perte de faim. | `allow` / `deny` |
| **`CHAT`** | Autorise ou interdit aux joueurs de parler dans le chat textuel. | `allow` / `deny` |
| **`REGIONAL_CHAT`** | Restreint la visibilité du chat aux seuls joueurs présents dans la même région. | `allow` / `deny` |
| **`ANNOUNCEMENT_INTERVAL`** | Diffuse périodiquement des messages de diffusion dans le chat de la région. | Message / Intervalle |
| **`ENTRY_TITLE`** | Affiche un titre au centre de l'écran des joueurs lorsqu'ils entrent dans la région. | Texte |
| **`ENTRY_SOUND`** | Joue un son lorsqu'un joueur entre dans la région. | Nom du son |
| **`ACTION_BAR`** | Envoie un message dans la barre d'action (au-dessus de l'inventaire) des joueurs présents. | Texte |
| **`BLOCKED_ITEMS`** | Liste d'objets (matériaux) interdits d'utilisation ou d'interaction dans la région. | Liste séparée par des virgules |
| **`BLOCKED_CRAFTING`** | Liste d'objets interdits de fabrication (crafting). | Liste séparée par des virgules |
| **`VILLAGER_TRADE`** | Autorise ou interdit le commerce avec les villageois. | `allow` / `deny` |
| **`TIME_LOCK`** | Bloque l'heure locale affichée pour les joueurs dans la région. | Heure (ex: `6000` pour midi) |
| **`WEATHER_LOCK`** | Bloque la météo locale affichée pour les joueurs. | `downfall` / `clear` |
| **`RESPAWN_LOCATION`** | Coordonnées personnalisées de réapparition en cas de mort dans la région. | Coordonnées |
