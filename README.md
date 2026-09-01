# ⚡ WorldX & WorldX-API

**WorldX** est une solution unifiée, ultra-performante et moderne d'édition de monde (WorldEdit-like) et de protection de régions (WorldGuard-like) conçue pour les serveurs Minecraft **Paper**, **Purpur** et **Folia** en **Java 21**.

---

## 🌟 Features Principales

- **⚡ Direct NMS Chunk Writing & File d'attente Asynchrone**:
  - Édition massive de blocs sans impacter le TPS du serveur via écriture directe en mémoire NMS (`LevelChunk.setBlockState`) et recalculs d'éclairage récursifs.
  - Progression en temps réel affichée dans la barre d'action (`ActionBar`).
  - Support du multithread régional **Folia** (`runAtLocation`).
- **🛡️ Protection de Régions & Spatial Indexing $O(1)$**:
  - Indexation spatiale par coordonnées de chunks (`SpatialIndex`) pour une recherche instantanée des règles de protection.
  - Support des formes de régions : **Cuboid**, **Polygone 2D/3D**, **Lasso (Convex Hull 3D)**, **Sphère** et **Global**.
  - Flags personnalisés & héritage des régions parentes (`priority`, `parent-id`).
- **🌲 Moteur de Reforestation Naturelle**:
  - Régénération automatique des arbres et forêts détruits (`natural-reforestation` flag) avec file d'attente $O(1)$ et effets visuels/sonores.
- **🌐 Synchronisation Réseau Multi-Serveurs (Redis)**:
  - Synchronisation en temps réel de l'état des régions entre plusieurs serveurs spigot via Redis Pub/Sub (`JedisPool`).
- **💾 Base de Données SQLite & MySQL avec HikariCP**:
  - Stockage des régions, des schématiques (avec versionnage d'historique), des dégradés/palettes et de l'historique d'annulation (`Undo/Redo`).
  - Système de rollback automatique (`/rollback` et expiration de région).
- **🌀 Moteur de Schématiques Animées 3D (`/schem animate`)**:
  - Animation 3D fluide à 60 FPS de structures complexes en mouvement via des entités `BlockDisplay`.
- **🏰 Moteur de Donjons Procéduraux (`/worldx dungeon`)**:
  - Génération automatique de donjons par assemblage de salles et couloirs avec protection et déclencheurs auto-générés.
- **⏳ Région Time Machine (`/rg timemachine <offset>`)**:
  - Reconstitution et restauration historique des régions avec matrice de particules temporelles.
- **🚩 Flags Révolutionnaires Exclusifs**:
  - `antigravity` : Effet d'apesanteur/lévitation lunaire sans effet de potion.
  - `custom-boss-bar` : Barre de boss Adventure dynamique avec mise en forme MiniMessage.
  - `no-elytra` / `flight-mode` : Interdiction d'Élytra ou autorisation du vol libre en survie.
  - `keep-inventory` & `respawn-location` : Conservation de l'inventaire/XP à la mort et point de réapparition personnalisé.
  - `entry-fee` & `tax-per-minute` : Prélèvement de frais d'entrée et taxe de séjour via l'économie.
  - `seamless-portal` : Transfert fluide inter-serveurs (BungeeCord/Velocity) au franchissement de région.

---

## 📦 Intégration Développeur (Maven & Gradle)

Vous pouvez intégrer facilement `WorldX-API` dans vos projets via **JitPack**.

### Maven (`pom.xml`)

Ajoutez le dépôt JitPack et la dépendance `WorldX-API` :

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- WorldX API Dependency -->
    <dependency>
        <groupId>com.github.Skytoone.WorldX</groupId>
        <artifactId>WorldX-API</artifactId>
        <version>1.0.0-SNAPSHOT</version> <!-- Remplacez par la version ou le tag souhaité -->
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle (`build.gradle`)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.Skytoone.WorldX:WorldX-API:1.0.0-SNAPSHOT'
}
```

---

## 💻 Exemples d'Utilisation de l'API

### 1. Accéder au Provider d'API

```java
import fr.skynex.worldx.api.WorldXAPI;
import fr.skynex.worldx.api.WorldXProvider;

public class MyPlugin {

    public void checkWorldX() {
        WorldXAPI api = WorldXProvider.get();
        System.out.println("WorldX version: " + api.getVersion());
    }
}
```

### 2. Récupérer une Région et vérifier les Flags

```java
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.region.Flag;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RegionExample {

    public boolean canPlayerBuild(Player player, Location location) {
        // Obtenir la région
        Region region = player.getWorld() != null ? ... : null;
        if (region != null) {
            // Vérification directe avec l'Enum Flag
            return region.isAllowed(Flag.BUILD);
        }
        return true;
    }
}
```

---

## ⚙️ Commandes Principales

| Commande | Description | Permission |
| :--- | :--- | :--- |
| `/wand` | Donne la hache de sélection d'édition | `worldx.wand` |
| `/set <bloc>` | Remplit la sélection avec le bloc spécifié | `worldx.edit` |
| `/replace <from> <to>` | Remplace un type de bloc par un autre | `worldx.edit` |
| `/undo` / `/redo` | Annule ou rétablit la dernière action | `worldx.edit` |
| `/sphere` / `/hsphere` | Génère une sphère pleine ou creuse | `worldx.edit` |
| `/rg define <id>` | Crée une région protégée | `worldx.region.create` |
| `/rg flag <id> <flag> <val>` | Définit la valeur d'un flag sur une région | `worldx.region.flag` |
| `/schem save/load` | Sauvegarde ou charge une schématique | `worldx.schematic` |
| `/brush` | Configure les pinceaux de peinture/sculpture | `worldx.brush` |

---

## 🛠️ Compilation à partir des Sources

Nécessite **Java 21** et **Maven**.

```bash
git clone https://github.com/Skytoone/WorldX.git
cd WorldX
mvn clean package
```

Les fichiers générés se trouveront dans :
- `WorldX-API/target/WorldX-API-1.0.0-SNAPSHOT.jar`
- `WorldX-Core/target/WorldX.jar` (Contient le plugin complet prêt à l'emploi)
