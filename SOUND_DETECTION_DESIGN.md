# Sound Detection System — Design-Dokument

## 1. Ubersicht

Das Sound-Detection-System erweitert die Mod um eine akustische Erkennungsmechanik. Mobs konnen den Spieler und andere Entities **horen**, auch wenn sie diese nicht sehen. Gerausche haben einen Typ-abhangigen Radius und losen das bestehende Such-Verhalten (`SearchLastKnownPositionGoal`) aus.

**Kernprinzip:** Sneaken = lautlos. Jede andere Bewegung erzeugt Gerausche, die Mobs in der Nahe alarmieren. Schwere Rustung und hohe Fallhohe machen mehr Larm. Pfeil-Einschlage erzeugen ebenfalls Gerausche — was als taktische Ablenkung genutzt werden kann.

### Zusammenspiel mit dem Sicht-System

```
Spieler bewegt sich
    |
    v
[Sound Event erzeugt] --> SoundDetectionManager pruft:
    |                       - Ist Sound-Detection aktiviert?
    |                       - Welcher Gerausch-Typ? Welcher Radius?
    |                       - Rustungs-Bonus?
    v
Alle Mobs im Radius prüfen:
    - Kann der Mob horen? (Whitelist/Blacklist)
    - Hat der Mob bereits ein Target? (ja → ignorieren)
    - Ist der Mob blacklisted? (Vision-Blacklist)
    v
[lastKnownTargetPos setzen] --> SearchLastKnownPositionGoal startet
    |
    v
Mob untersucht Position (bestehendes Verhalten)
```

---

## 2. Gerausch-Events

### 2.1 Spieler-Gerausche

| Gerausch-Typ | Basis-Radius | Bedingung | Anmerkungen |
|---|---|---|---|
| `LANDING` | `fallDistance * 3.0` Blocke (min. 6, max. 48) | Spieler landet nach Fall | Fallhohe < 1.5 Blocke erzeugt kein Gerausch |
| `SPRINTING` | 16 Blocke | Spieler sprintet | Alle 10 Ticks ein Event |
| `WALKING` | 8 Blocke | Spieler lauft (nicht sprinten, nicht sneaken) | Alle 15 Ticks ein Event |
| `SNEAKING` | — | — | Erzeugt **kein** Gerausch |
| `ATTACK` | 12 Blocke | Spieler greift Entity an | Nur wenn das Ziel den Angriff uberlebt |
| `ATTACK_STEALTH` | — | Spieler totet Entity mit einem Schlag | Kein Gerausch — Meuchelkill |

### 2.2 Rustungs-Bonus

Beim Laufen und Sprinten wird der Radius multipliziert, wenn der Spieler schwere Rustung tragt:

| Rustungs-Anteil | Multiplikator |
|---|---|
| 0 schwere Teile | x1.0 |
| 1 schweres Teil | x1.15 |
| 2 schwere Teile | x1.3 |
| 3 schwere Teile | x1.5 |
| 4 schwere Teile (Vollrustung) | x1.75 |

**Schwere Materialien:** Eisen, Diamant, Netherite.
**Leichte Materialien (kein Bonus):** Leder, Kette, Gold, Schildkroten-Helm.

Der Bonus wird nur auf `WALKING` und `SPRINTING` angewendet, nicht auf `LANDING` oder `ATTACK`.

### 2.3 Entity/Projektil-Gerausche

| Gerausch-Typ | Basis-Radius | Bedingung |
|---|---|---|
| `PROJECTILE_IMPACT` | 12 Blocke | Pfeil, Schneeball, Ei, Dreizack etc. schlagt ein |

**Taktischer Nutzen:** Spieler kann einen Pfeil in eine Ecke schiessen, um Mobs dorthin zu locken und dann in die andere Richtung zu schleichen.

---

## 3. Architektur

### 3.1 Neue Klassen

```
src/main/java/com/dysmn/doyouseemenow/
├── sound/
│   ├── SoundType.java                 # Enum: LANDING, SPRINTING, WALKING, ATTACK, PROJECTILE_IMPACT
│   ├── SoundEvent.java                # Record: Position + Radius + SoundType + Verursacher
│   └── SoundDetectionManager.java     # Server-seitig: verarbeitet Sound-Events, alarmiert Mobs
└── mixin/
    ├── PlayerMovementSoundMixin.java  # Erkennt Laufen, Sprinten, Landen
    ├── PlayerAttackSoundMixin.java    # Erkennt Angriffe + One-Hit-Kill-Check
    └── ProjectileImpactSoundMixin.java # Erkennt Projektil-Einschlage
```

### 3.2 SoundType (Enum)

```java
public enum SoundType {
    LANDING,
    SPRINTING,
    WALKING,
    ATTACK,
    PROJECTILE_IMPACT;
}
```

### 3.3 SoundEvent (Record)

```java
public record SoundEvent(
    Vec3d position,       // Wo das Gerausch entsteht
    double radius,        // Effektiver Radius (nach Multiplikatoren)
    SoundType type,       // Gerausch-Typ
    @Nullable Entity source // Verursacher (Spieler/Projektil), null-safe
) {}
```

### 3.4 SoundDetectionManager

Zentrale Server-Klasse, die Sound-Events verarbeitet.

```java
public final class SoundDetectionManager {

    /**
     * Ein Gerausch-Event registrieren. Wird von Mixins aufgerufen.
     * Sucht alle hoerenden Mobs im Radius und setzt deren lastKnownTargetPos.
     */
    public static void emitSound(ServerWorld world, SoundEvent event) {
        if (!ModConfig.get().soundDetectionEnabled) return;

        double radius = event.radius() * getConfigMultiplier(event.type());

        // Box-basierte Entity-Suche (effizient, nutzt Minecrafts Entity-Lookup)
        Box searchBox = new Box(
            event.position().subtract(radius, radius, radius),
            event.position().add(radius, radius, radius)
        );

        List<MobEntity> nearbyMobs = world.getEntitiesByClass(
            MobEntity.class, searchBox,
            mob -> canMobHear(mob, event, radius)
        );

        for (MobEntity mob : nearbyMobs) {
            // Streuung basierend auf Distanz (weiter weg = ungenauer)
            Vec3d investigatePos = addInaccuracy(mob, event.position());
            ((LastKnownPositionAccess) mob).dysmn$setLastKnownTargetPos(investigatePos);
        }
    }

    private static boolean canMobHear(MobEntity mob, SoundEvent event, double radius) {
        // 1. Mob hat bereits ein Ziel → ignoriert Gerausche
        if (mob.getTarget() != null) return false;

        // 2. Mob ist auf der Vision-Blacklist → hoert auch nichts
        if (ModConfig.get().isBlacklisted(mob)) return false;

        // 3. Mob darf horen? (Hearing-Liste pruefen)
        if (!ModConfig.get().canMobHear(mob)) return false;

        // 4. Verursacher ist der Mob selbst → ignorieren
        if (event.source() != null && event.source() == mob) return false;

        // 5. Distanz pruefen (genauer als Box-Check)
        double distance = mob.getPos().distanceTo(event.position());
        return distance <= radius;

        // Hinweis: Kein Wand/Block-Check — Schall geht durch Waende.
        // Das ist bewusst so: Minecraft-Waende sind duenn, und ein
        // Raycast waere zu teuer fuer viele Mobs gleichzeitig.
    }

    private static Vec3d addInaccuracy(MobEntity mob, Vec3d soundPos) {
        double distance = mob.getPos().distanceTo(soundPos);
        // Ungenauigkeit: 10% der Distanz, max. 4 Blöcke
        double inaccuracy = Math.min(distance * 0.1, 4.0);
        if (inaccuracy < 0.5) return soundPos;

        double ox = (mob.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
        double oz = (mob.getRandom().nextDouble() - 0.5) * 2.0 * inaccuracy;
        return soundPos.add(ox, 0, oz);
    }
}
```

### 3.5 Mixins

#### PlayerMovementSoundMixin

**Ziel:** `ServerPlayerEntity` oder `LivingEntity`
**Methode:** `tick()` bzw. `fall()` / `handleFallDamage()`

```java
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerMovementSoundMixin {

    @Unique private int dysmn_movementSoundCooldown = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void doYouSeeMeNow_checkMovementSound(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.isSneaking()) return; // Sneaken = lautlos

        if (dysmn_movementSoundCooldown > 0) {
            dysmn_movementSoundCooldown--;
            return;
        }

        if (player.isSprinting() && player.isOnGround()) {
            double radius = applyArmorBonus(player, ModConfig.get().sprintSoundRadius);
            SoundDetectionManager.emitSound(player.getServerWorld(),
                new SoundEvent(player.getPos(), radius, SoundType.SPRINTING, player));
            dysmn_movementSoundCooldown = ModConfig.get().sprintSoundInterval;
        } else if (isWalking(player)) {
            double radius = applyArmorBonus(player, ModConfig.get().walkSoundRadius);
            SoundDetectionManager.emitSound(player.getServerWorld(),
                new SoundEvent(player.getPos(), radius, SoundType.WALKING, player));
            dysmn_movementSoundCooldown = ModConfig.get().walkSoundInterval;
        }
    }
}
```

**Landing-Erkennung:**

```java
@Mixin(LivingEntity.class)
// Alternativ: ServerPlayerEntity
```

Hook in `handleFallDamage()` oder `onLanding()`. Die `fallDistance` des Spielers bestimmt den Radius:

```java
double radius = Math.min(Math.max(fallDistance * 3.0, 6.0), 48.0);
```

Nur wenn `fallDistance >= 1.5`.

#### PlayerAttackSoundMixin

**Ziel:** `ServerPlayerEntity`
**Methode:** `attack(Entity target)`

```java
@Inject(method = "attack", at = @At("TAIL"))
private void doYouSeeMeNow_attackSound(Entity target, CallbackInfo ci) {
    ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

    // One-Hit-Kill? → kein Gerausch (Stealth Kill)
    if (target instanceof LivingEntity living && living.isDead()) {
        return; // Meuchelkill — lautlos
    }

    SoundDetectionManager.emitSound(player.getServerWorld(),
        new SoundEvent(target.getPos(), ModConfig.get().attackSoundRadius, SoundType.ATTACK, player));
}
```

**Wichtig:** Der Check auf `isDead()` muss nach dem Angriff passieren (deshalb `@At("TAIL")`). Wenn das Ziel tot ist, war es ein One-Hit-Kill → kein Gerausch.

#### ProjectileImpactSoundMixin

**Ziel:** `ProjectileEntity`
**Methode:** `onCollision(HitResult)` oder `onEntityHit()` / `onBlockHit()`

```java
@Mixin(ProjectileEntity.class)
public abstract class ProjectileImpactSoundMixin {

    @Inject(method = "onCollision", at = @At("TAIL"))
    private void doYouSeeMeNow_projectileImpactSound(HitResult hitResult, CallbackInfo ci) {
        ProjectileEntity projectile = (ProjectileEntity) (Object) this;
        if (projectile.getWorld().isClient()) return;

        Vec3d impactPos = hitResult.getPos();
        ServerWorld world = (ServerWorld) projectile.getWorld();

        SoundDetectionManager.emitSound(world,
            new SoundEvent(impactPos, ModConfig.get().projectileImpactRadius,
                SoundType.PROJECTILE_IMPACT, projectile));
    }
}
```

---

## 4. Integration mit bestehendem Code

### 4.1 SearchLastKnownPositionGoal — Keine Anderungen notig

Das bestehende `SearchLastKnownPositionGoal` reagiert bereits auf `lastKnownTargetPos`. Das Sound-System setzt einfach dieses Feld — der Rest (TURN_TOWARD → WALK_TO → LOOK_AROUND) lauft automatisch ab.

```
Sound Event → SoundDetectionManager → lastKnownTargetPos setzen
                                         ↓
                              SearchLastKnownPositionGoal
                              startet automatisch (canStart() prüft lastKnownTargetPos)
```

### 4.2 LastKnownPositionAccess — Keine Anderungen notig

Das Interface und das Mixin (`MobEntityDataMixin`) bleiben unverandert. Sound-Events nutzen denselben `dysmn$setLastKnownTargetPos(Vec3d)` Mechanismus.

### 4.3 DoYouSeeMeNow (Server Entrypoint) — Minimale Anderung

Die Goal-Registrierung bleibt gleich. Es muss **nichts** am Entrypoint geandert werden, da die Sound-Erkennung uber Mixins und den statischen `SoundDetectionManager` lauft.

### 4.4 MobDamageMixin — Koexistenz

Der bestehende `MobDamageMixin` setzt `lastKnownTargetPos` bei Schaden. Das Sound-System kann ebenfalls diese Position setzen. Das ist kein Konflikt — das `SearchLastKnownPositionGoal` reagiert in `shouldContinue()` bereits auf Position-Updates:

```java
// Zeile 164-170 in SearchLastKnownPositionGoal.java
Vec3d newPos = ((LastKnownPositionAccess) mob).dysmn$getLastKnownTargetPos();
if (newPos != null && !newPos.equals(targetPos)) {
    targetPos = newPos;
    phase = Phase.TURN_TOWARD;
    timer = 0;
}
```

Wenn ein Mob ein Gerausch hort wahrend er schon sucht, wird die neue Position ubernommen.

### 4.5 Visuelle Indikatoren

Das "?" Partikel-System wird automatisch durch den `SearchLastKnownPositionGoal` getriggert — auch bei gerauschbasierten Suchen. Kein zusatzlicher Code notig.

---

## 5. Config-Erweiterungen

Neue Felder in `ModConfig.java`:

```java
// --- Sound Detection ---

/** Globaler Toggle: Sound-Detection an/aus (default: true) */
public boolean soundDetectionEnabled = true;

/** Basis-Radius fuer Sprint-Geraeusche in Bloecken (default: 16) */
public double sprintSoundRadius = 16.0;

/** Intervall zwischen Sprint-Geraeusch-Events in Ticks (default: 10) */
public int sprintSoundInterval = 10;

/** Basis-Radius fuer Lauf-Geraeusche in Bloecken (default: 8) */
public double walkSoundRadius = 8.0;

/** Intervall zwischen Lauf-Geraeusch-Events in Ticks (default: 15) */
public int walkSoundInterval = 15;

/** Minimale Fallhoehe ab der ein Lande-Gerausch erzeugt wird (default: 1.5) */
public double landingMinFallDistance = 1.5;

/** Radius-Multiplikator fuer Lande-Geraeusche: fallDistance * dieser Wert (default: 3.0) */
public double landingRadiusMultiplier = 3.0;

/** Maximaler Radius fuer Lande-Geraeusche (default: 48.0) */
public double landingMaxRadius = 48.0;

/** Minimaler Radius fuer Lande-Geraeusche (default: 6.0) */
public double landingMinRadius = 6.0;

/** Basis-Radius fuer Angriffs-Geraeusche in Bloecken (default: 12) */
public double attackSoundRadius = 12.0;

/** Basis-Radius fuer Projektil-Einschlaege in Bloecken (default: 12) */
public double projectileImpactRadius = 12.0;

/** Rustungs-Bonus pro schweres Teil (addiert auf 1.0) (default: 0.1875) */
public double heavyArmorBonusPerPiece = 0.1875;

/**
 * Hearing-Modus: "whitelist" oder "blacklist" (default: "blacklist")
 * - "blacklist": Alle Mobs koennen hoeren, ausser die hier gelisteten
 * - "whitelist": Nur die hier gelisteten Mobs koennen hoeren
 */
public String hearingMode = "blacklist";

/**
 * Mob-Liste fuer Hearing (je nach hearingMode als White- oder Blacklist)
 * Bei "blacklist": Mobs die NICHT hoeren koennen
 * Bei "whitelist": Mobs die hoeren koennen
 */
public List<String> hearingMobList = List.of(
    // Default Blacklist: Mobs die nicht hoeren sollen
    // (leer = alle koennen hoeren)
);
```

### Neue Methode in ModConfig:

```java
private transient Set<EntityType<?>> hearingMobTypes;

public boolean canMobHear(MobEntity mob) {
    if (!soundDetectionEnabled) return false;

    if (hearingMobTypes == null) {
        hearingMobTypes = new HashSet<>();
        for (String id : hearingMobList) {
            var identifier = Identifier.tryParse(id);
            if (identifier != null) {
                Registries.ENTITY_TYPE.getOrEmpty(identifier)
                    .ifPresent(hearingMobTypes::add);
            }
        }
    }

    boolean inList = hearingMobTypes.contains(mob.getType());
    return hearingMode.equals("whitelist") ? inList : !inList;
}
```

### Beispiel-Config (JSON)

```json
{
  "soundDetectionEnabled": true,
  "sprintSoundRadius": 16.0,
  "sprintSoundInterval": 10,
  "walkSoundRadius": 8.0,
  "walkSoundInterval": 15,
  "landingMinFallDistance": 1.5,
  "landingRadiusMultiplier": 3.0,
  "landingMaxRadius": 48.0,
  "landingMinRadius": 6.0,
  "attackSoundRadius": 12.0,
  "projectileImpactRadius": 12.0,
  "heavyArmorBonusPerPiece": 0.1875,
  "hearingMode": "blacklist",
  "hearingMobList": []
}
```

---

## 6. Netzwerk-Pakete

### Kein neues Paket notwendig

Das Sound-System arbeitet komplett server-seitig. Die Client-Anzeige lauft uber das bestehende `mob_searching` Paket, da `SearchLastKnownPositionGoal` automatisch das "?" Partikel-System triggert.

### Optional fur spater: Sound-Indikator-Paket

Falls gewunscht ein visueller Hinweis, dass ein Mob ein Gerausch gehort hat (z.B. ein Ohr-Symbol oder andere Partikel), konnte ein neues Paket eingefuhrt werden:

```java
// NetworkConstants.java
public static final Identifier MOB_HEARD_SOUND_PACKET =
    new Identifier(DoYouSeeMeNow.MOD_ID, "mob_heard_sound");
```

**Inhalt:** `int entityId`
**Richtung:** Server → Client

Dies ist **nicht** Teil der initialen Implementierung, sondern ein mogliches Follow-up.

---

## 7. Performance-Uberlegungen

### 7.1 Nicht jeden Tick pruefen

- **Sprint-Events:** Alle 10 Ticks (konfiguierbar via `sprintSoundInterval`)
- **Walk-Events:** Alle 15 Ticks (konfigurierbar via `walkSoundInterval`)
- **Landing-Events:** Nur einmalig bei Landung (Event-basiert, kein Polling)
- **Attack-Events:** Nur bei tatsaechlichem Angriff (Event-basiert)
- **Projektil-Events:** Nur bei Einschlag (Event-basiert)

→ Im Worst Case (Spieler sprintet) wird alle 10 Ticks (0.5s) eine Mob-Suche ausgefuhrt.

### 7.2 Effiziente Mob-Suche

`world.getEntitiesByClass()` nutzt Minecrafts internes Entity-Lookup mit Chunk-Sektionen. Die Box-Suche ist bereits optimiert und muss nicht manuell implementiert werden.

**Radius-Begrenzung:** Der maximale Sound-Radius von 48 Blocken (Landing aus grosser Hohe) bedeutet eine Suchbox von 96x96x96. Das ist akzeptabel, da:
- Die meisten Sound-Events deutlich kleinere Radien haben (8-16 Blocke)
- Landing-Events selten sind (einmalig, nicht pro Tick)
- `getEntitiesByClass()` frueh filtert (Chunk-basiert)

### 7.3 Keine Raycast-Pruefungen

Schall geht bewusst durch Waende. Das vermeidet teure Raycasts pro Mob und ist gameplay-technisch sinnvoll: duenne Minecraft-Waende sollten Geraeusche nicht komplett blocken.

### 7.4 Cooldown pro Mob (nicht implementiert, aber vorbereitet)

Falls sich herausstellt, dass ein Mob zu oft durch Gerausche getriggert wird (z.B. Spieler rennt dauerhaft in der Nahe), konnte ein Cooldown-Feld im `MobEntityDataMixin` ergaenzt werden:

```java
@Unique private int dysmn_soundCooldown = 0;
```

Dies ist **nicht** Teil der initialen Implementierung, da `SearchLastKnownPositionGoal` bereits nur startet wenn kein Target gesetzt ist, und waehrend der Suche neue Positionen ubernimmt statt neue Goals zu starten.

---

## 8. Stealth-Indikator (HUD)

### 8.1 Übersicht

Wenn der Spieler sneakt, erscheint ein minimalistisches HUD-Element, das einen **Stealth-Score (0–100%)** anzeigt. Der Score gibt Feedback, wie gut der Spieler gerade versteckt ist — je höher, desto unwahrscheinlicher eine Entdeckung.

Das gesamte Feature ist **rein client-seitig** implementiert. Alle benötigten Daten (Lichtlevel, Mob-Positionen, Rüstung, Bewegung) sind auf dem Client bereits verfügbar.

### 8.2 Stealth-Score — Faktoren und Berechnung

Der Score setzt sich aus fünf gewichteten Faktoren zusammen:

| Faktor | Gewicht | Bereich | Berechnung |
|--------|---------|---------|------------|
| **Lichtlevel** | 35% | 0–100 | `(15 - lightLevel) / 15 * 100` — Dunkelheit = hoher Score |
| **Rüstung** | 15% | 0–100 | `(4 - heavyArmorPieces) / 4 * 100` — Weniger schwere Rüstung = besser |
| **Mob-Nähe** | 25% | 0–100 | Distanz zum nächsten Mob relativ zur Erkennungs-Reichweite (weiter weg = besser) |
| **Bewegung** | 15% | 0–100 | Still stehen = 100, sich bewegen beim Sneaken = 30 |
| **FOV-Sichtbarkeit** | 10% | 0–100 | Im FOV eines Mobs = 0, außerhalb aller FOVs = 100 |

**Gesamtscore:**
```
stealthScore = (light * 0.35) + (armor * 0.15) + (proximity * 0.25)
             + (movement * 0.15) + (fov * 0.10)
```

#### Faktor-Details

**Lichtlevel:**
```java
int light = world.getLightLevel(player.getBlockPos());
double lightScore = (15.0 - light) / 15.0 * 100.0;
```
Nutzt das kombinierte Lichtlevel (Block + Sky) am Standort des Spielers. Bei Lichtlevel 0 (totale Dunkelheit) bekommt der Spieler die vollen 35% Gewichtung.

**Rüstung:**
```java
int heavyPieces = countHeavyArmorPieces(player); // Eisen, Diamant, Netherite
double armorScore = (4.0 - heavyPieces) / 4.0 * 100.0;
```
Schwere Rüstung glänzt und ist klobig — sie macht den Spieler auch beim Sneaken sichtbarer. Nutzt dieselbe Logik wie der Rüstungs-Bonus im Sound-System (gleiche Definition von "schwer": Eisen, Diamant, Netherite).

**Mob-Nähe:**
```java
double nearestDist = findNearestMobDistance(player, 48.0); // Suchradius
double maxRange = VisibilityCheck.getDetectionRange(lightLevel); // Licht-basiert
double proximityScore = Math.min(nearestDist / maxRange, 1.0) * 100.0;
```
Vergleicht die Distanz zum nächsten Mob mit der lichtbasierten Erkennungs-Reichweite. Ist kein Mob in 48 Blöcken → volle Punktzahl.

**Bewegung:**
```java
double speed = player.getVelocity().horizontalLength();
double movementScore = speed < 0.001 ? 100.0 : 30.0;
```
Binäre Unterscheidung: Still stehen beim Sneaken gibt volle Punkte, jede Bewegung reduziert auf 30. Sneaken an sich ist bereits leise — aber Stillstand ist optimal.

**FOV-Sichtbarkeit:**
```java
boolean inAnyFov = false;
for (MobEntity mob : nearbyMobs) {
    if (VisibilityCheck.isInFieldOfView(mob, player)) {
        inAnyFov = true;
        break;
    }
}
double fovScore = inAnyFov ? 0.0 : 100.0;
```
Prüft ob der Spieler im Sichtfeld (120° FOV) irgendeines nahegelegenen Mobs steht. Nutzt `VisibilityCheck.isInFieldOfView()` — dieselbe Logik wie das Sicht-System.

### 8.3 Architektur

```
src/main/java/com/dysmn/doyouseemenow/
└── client/
    ├── StealthCalculator.java    # Berechnet den Stealth-Score aus allen Faktoren
    └── StealthHudRenderer.java   # Rendert das HUD-Element
```

#### StealthCalculator

```java
public final class StealthCalculator {

    private static double smoothedScore = 0.0;
    private static final double SMOOTHING = 0.08; // Sanftes Gleiten, kein Springen

    /**
     * Berechnet den aktuellen Stealth-Score (0.0–1.0).
     * Wird jeden Client-Tick aufgerufen wenn der Spieler sneakt.
     */
    public static double calculate(ClientPlayerEntity player, ClientWorld world) {
        int lightLevel = world.getLightLevel(player.getBlockPos());
        double lightScore = (15.0 - lightLevel) / 15.0;

        int heavyPieces = countHeavyArmorPieces(player);
        double armorScore = (4.0 - heavyPieces) / 4.0;

        double nearestDist = findNearestMobDistance(player, world, 48.0);
        double maxRange = Math.max(lightLevel * 2.5, 4.0);
        double proximityScore = Math.min(nearestDist / maxRange, 1.0);

        double speed = player.getVelocity().horizontalLength();
        double movementScore = speed < 0.001 ? 1.0 : 0.3;

        boolean inFov = isInAnyMobFov(player, world, 32.0);
        double fovScore = inFov ? 0.0 : 1.0;

        double rawScore = (lightScore * 0.35) + (armorScore * 0.15)
                        + (proximityScore * 0.25) + (movementScore * 0.15)
                        + (fovScore * 0.10);

        // Sanftes Gleiten zum Zielwert (kein abruptes Springen)
        smoothedScore += (rawScore - smoothedScore) * SMOOTHING;
        return smoothedScore;
    }

    public static void reset() {
        smoothedScore = 0.0;
    }

    private static int countHeavyArmorPieces(PlayerEntity player) {
        int count = 0;
        for (ItemStack stack : player.getArmorItems()) {
            if (stack.getItem() instanceof ArmorItem armor) {
                ArmorMaterial mat = armor.getMaterial();
                if (mat == ArmorMaterials.IRON || mat == ArmorMaterials.DIAMOND
                        || mat == ArmorMaterials.NETHERITE) {
                    count++;
                }
            }
        }
        return count;
    }

    private static double findNearestMobDistance(ClientPlayerEntity player,
                                                  ClientWorld world, double searchRadius) {
        Box searchBox = player.getBoundingBox().expand(searchRadius);
        double nearest = searchRadius; // Default: kein Mob in Reichweite
        for (Entity entity : world.getEntitiesByClass(MobEntity.class, searchBox, e -> true)) {
            double dist = player.distanceTo(entity);
            if (dist < nearest) nearest = dist;
        }
        return nearest;
    }

    private static boolean isInAnyMobFov(ClientPlayerEntity player,
                                          ClientWorld world, double searchRadius) {
        Box searchBox = player.getBoundingBox().expand(searchRadius);
        for (Entity entity : world.getEntitiesByClass(MobEntity.class, searchBox, e -> true)) {
            if (entity instanceof MobEntity mob) {
                if (VisibilityCheck.isInFieldOfView(mob, player)) {
                    return true;
                }
            }
        }
        return false;
    }
}
```

**Wichtig:** Der `smoothedScore` wird mit einem Smoothing-Faktor von 0.08 interpoliert, damit der Score sanft gleitet statt bei jedem Tick zu springen. Das fühlt sich für den Spieler natürlicher an.

#### StealthHudRenderer

```java
public final class StealthHudRenderer {

    private static float fadeAlpha = 0.0f;
    private static final float FADE_SPEED = 0.05f; // ~1s Ein-/Ausblenden

    /**
     * Wird im HUD-Render-Event (Fabric API: HudRenderCallback) aufgerufen.
     */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        boolean sneaking = player.isSneaking();

        // Fade ein/aus
        if (sneaking) {
            fadeAlpha = Math.min(fadeAlpha + FADE_SPEED, 1.0f);
        } else {
            fadeAlpha = Math.max(fadeAlpha - FADE_SPEED, 0.0f);
            if (fadeAlpha <= 0.0f) {
                StealthCalculator.reset();
                return;
            }
        }

        double score = StealthCalculator.calculate(player, client.world);
        int percentage = (int) (score * 100);

        // Position: unten links, über der Hotbar
        int x = 10;
        int y = context.getScaledWindowHeight() - 50;

        int alpha = (int) (fadeAlpha * 255);
        int color = getScoreColor(score, alpha);

        // Augen-Symbol: öffnet/schliesst sich basierend auf Score
        String eyeSymbol = getEyeSymbol(score);

        // Hintergrund (halbtransparent)
        int bgAlpha = (int) (fadeAlpha * 100);
        context.fill(x - 2, y - 2, x + 60, y + 12, (bgAlpha << 24));

        // Augen-Symbol + Prozentwert
        context.drawTextWithShadow(client.textRenderer,
            eyeSymbol + " " + percentage + "%",
            x, y, color);

        // Score-Leiste darunter
        int barWidth = 56;
        int barHeight = 3;
        int barY = y + 12;
        int filledWidth = (int) (barWidth * score);

        // Leisten-Hintergrund
        context.fill(x, barY, x + barWidth, barY + barHeight,
            (bgAlpha << 24) | 0x333333);
        // Leisten-Füllung
        context.fill(x, barY, x + filledWidth, barY + barHeight,
            color);
    }

    /**
     * Farbe basierend auf Score:
     * 0-30%:  Rot (hohes Entdeckungsrisiko)
     * 30-70%: Gelb (mittleres Risiko)
     * 70-100%: Grün (gut versteckt)
     */
    private static int getScoreColor(double score, int alpha) {
        if (score < 0.3) {
            return (alpha << 24) | 0xFF4444; // Rot
        } else if (score < 0.7) {
            return (alpha << 24) | 0xFFAA00; // Gelb/Orange
        } else {
            return (alpha << 24) | 0x44FF44; // Grün
        }
    }

    /**
     * Augen-Symbol basierend auf Score:
     * Hoch   → ◉ (geschlossenes Auge / gut versteckt)
     * Mittel → ◎ (halb offenes Auge)
     * Niedrig→ ⊙ (offenes Auge / leicht zu entdecken)
     */
    private static String getEyeSymbol(double score) {
        if (score >= 0.7) return "◉";      // Gut versteckt
        else if (score >= 0.3) return "◎";  // Mittleres Risiko
        else return "⊙";                   // Hohes Risiko
    }
}
```

### 8.4 Registrierung

In `DoYouSeeMeNowClient.java` (Client Entrypoint):

```java
@Override
public void onInitializeClient() {
    // ... bestehender Code ...

    // Stealth-HUD registrieren
    HudRenderCallback.EVENT.register(StealthHudRenderer::render);
}
```

Fabric API stellt `HudRenderCallback` bereit — kein Mixin nötig für HUD-Rendering.

### 8.5 HUD-Design

```
┌──────────────────────────────────────────────┐
│                                              │
│                                              │
│                                              │
│                                              │
│                                              │
│                                              │
│  ◉ 82%                                      │
│  ████████████████░░░░                        │
│  ┌─────────────────────────────────────┐     │
│  │         [Hotbar]                    │     │
│  └─────────────────────────────────────┘     │
└──────────────────────────────────────────────┘

Zustände:
  ⊙ 15%  ██░░░░░░░░░░░░░░░░   [Rot]     — Hohes Risiko
  ◎ 48%  ████████░░░░░░░░░░   [Gelb]    — Mittleres Risiko
  ◉ 82%  ████████████████░░   [Grün]    — Gut versteckt
```

- **Position:** Unten links, über der Hotbar
- **Fade:** ~1 Sekunde Ein-/Ausblenden beim Start/Ende des Sneakens
- **Smoothing:** Score gleitet sanft (kein Flackern bei Grenzwerten)
- **Farben:** Rot → Gelb → Grün je nach Score-Bereich
- **Symbol:** Variiert mit dem Score (drei Stufen)

### 8.6 Performance

- **Berechnung nur beim Sneaken** — kein Overhead wenn der Spieler normal läuft
- **Mob-Suche:** `getEntitiesByClass()` mit 48/32-Block-Radius, nur client-seitig
- **Einmal pro Tick** — nicht pro Frame (Score wird gecacht, nur Rendering pro Frame)
- **Smoothing** reduziert visuelle Unruhe ohne zusätzliche Berechnung
- **Kein Netzwerk-Overhead** — rein client-seitig, keine neuen Pakete

---

## 9. Implementation Plan

### Phase 1: Grundstruktur (keine Gameplay-Auswirkung)

1. **`SoundType.java`** erstellen — Enum mit allen Gerausch-Typen
2. **`SoundEvent.java`** erstellen — Record fur Gerausch-Daten
3. **`SoundDetectionManager.java`** erstellen — `emitSound()` Methode mit Mob-Suche und `lastKnownTargetPos`-Setzung
4. **`ModConfig.java`** erweitern — Alle neuen Sound-Felder + `canMobHear()` Methode

### Phase 2: Spieler-Bewegungsgerausche

5. **`PlayerMovementSoundMixin.java`** erstellen:
   - Sprint-Erkennung mit Cooldown
   - Walk-Erkennung mit Cooldown
   - Sneaking-Ausnahme
   - Rustungs-Bonus-Berechnung (Helper-Methode `applyArmorBonus()` im Manager oder Mixin)
6. **Landing-Erkennung** im selben oder separaten Mixin:
   - Hook in `handleFallDamage()` oder `onLanding()`
   - `fallDistance`-basierter Radius

### Phase 3: Angriffs-Gerausche

7. **`PlayerAttackSoundMixin.java`** erstellen:
   - Hook in `ServerPlayerEntity.attack()`
   - One-Hit-Kill-Detection (Target tot nach Angriff → kein Sound)

### Phase 4: Projektil-Gerausche

8. **`ProjectileImpactSoundMixin.java`** erstellen:
   - Hook in `ProjectileEntity.onCollision()`
   - Server-Side-Only-Check

### Phase 5: Mixin-Registrierung & Test

9. **`do_you_see_me_now.mixins.json`** aktualisieren — Neue Mixins registrieren
10. **Funktionstest:**
    - Sprint/Walk erzeugt Sound → Mob kommt
    - Sneaken erzeugt keinen Sound
    - Landing erzeugt Sound proportional zur Fallhohe
    - Angriff erzeugt Sound, One-Hit-Kill nicht
    - Pfeil-Einschlag lockt Mobs an (Ablenkung)
    - Schwere Rustung erhoht Sprint/Walk-Radius
    - Config-Toggle funktioniert
    - Hearing Whitelist/Blacklist funktioniert

### Phase 6: Stealth-Indikator (HUD)

11. **`StealthCalculator.java`** erstellen — Score-Berechnung aus allen 5 Faktoren mit Smoothing
12. **`StealthHudRenderer.java`** erstellen — HUD-Rendering mit Augen-Symbol, Prozentwert, Leiste und Fade
13. **`DoYouSeeMeNowClient.java`** erweitern — `HudRenderCallback` registrieren
14. **Funktionstest:**
    - HUD erscheint nur beim Sneaken (mit Fade)
    - Score reagiert korrekt auf Lichtlevel (dunkel = hoch)
    - Schwere Rüstung senkt den Score
    - Score sinkt bei Mob-Nähe
    - Stillstand vs. Bewegung beim Sneaken zeigt Unterschied
    - Im FOV eines Mobs stehen senkt den Score
    - Farben wechseln korrekt (Rot/Gelb/Grün)
    - Score gleitet sanft (kein Flackern)

### Phase 7: Feinschliff (optional)

15. **Balancing:** Radien und Intervalle anpassen basierend auf Gameplay-Tests
16. **Sound-Heard-Partikel:** Optionales neues Paket + Client-Partikel wenn ein Mob ein Gerausch hort (z.B. kleine Noten-Partikel)
17. **Stealth-HUD-Tuning:** Gewichtungen und Schwellenwerte der Score-Faktoren basierend auf Playtesting anpassen

---

## Zusammenfassung der neuen/geänderten Dateien

| Datei | Aktion | Beschreibung |
|---|---|---|
| `sound/SoundType.java` | **Neu** | Enum fur Gerausch-Typen |
| `sound/SoundEvent.java` | **Neu** | Record: Position, Radius, Typ, Quelle |
| `sound/SoundDetectionManager.java` | **Neu** | Zentrale Verarbeitung von Sound-Events |
| `mixin/PlayerMovementSoundMixin.java` | **Neu** | Sprint/Walk/Landing Erkennung |
| `mixin/PlayerAttackSoundMixin.java` | **Neu** | Angriffs-Gerausch + Stealth-Kill |
| `mixin/ProjectileImpactSoundMixin.java` | **Neu** | Projektil-Einschlag-Gerausch |
| `ModConfig.java` | **Ändern** | Sound-Config-Felder + `canMobHear()` |
| `do_you_see_me_now.mixins.json` | **Ändern** | Neue Mixins registrieren |
| `client/StealthCalculator.java` | **Neu** | Client-seitige Score-Berechnung (5 Faktoren + Smoothing) |
| `client/StealthHudRenderer.java` | **Neu** | HUD-Rendering: Augen-Symbol, Prozentwert, Score-Leiste |
| `DoYouSeeMeNowClient.java` | **Ändern** | `HudRenderCallback` für Stealth-HUD registrieren |

**Keine Änderungen an:**
- `SearchLastKnownPositionGoal.java`
- `LastKnownPositionAccess.java`
- `MobEntityDataMixin.java`
- `VisibilityCheck.java`
- `NetworkConstants.java` (erst bei optionalen Sound-Partikeln)
- `SpottedTracker.java`, `SpottedParticles.java`
