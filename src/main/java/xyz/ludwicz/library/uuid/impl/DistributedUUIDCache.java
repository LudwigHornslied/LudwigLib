package xyz.ludwicz.library.uuid.impl;

import org.bukkit.scheduler.BukkitRunnable;
import xyz.ludwicz.library.LudwigLib;
import xyz.ludwicz.library.uuid.LudwigUUIDCache;
import xyz.ludwicz.library.uuid.UUIDCache;
import xyz.ludwicz.library.xpacket.LudwigXPacketHandler;
import xyz.ludwicz.library.xpacket.XPacket;

import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DistributedUUIDCache
        implements UUIDCache {
    private static final Map<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private static final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    public DistributedUUIDCache() {
        LudwigLib.getInstance().runBackboneRedisCommand(redis -> {
            Map<String, String> cache = redis.hgetAll("UUIDCache");
            for (Map.Entry<String, String> cacheEntry : cache.entrySet()) {
                UUID uuid = UUID.fromString(cacheEntry.getKey());
                String name = cacheEntry.getValue();
                uuidToName.put(uuid, name);
                nameToUuid.put(name.toLowerCase(), uuid);
            }
            return null;
        });
    }

    @Override
    public UUID uuid(String name) {
        return nameToUuid.get(name.toLowerCase());
    }

    @Override
    public String name(UUID uuid) {
        return uuidToName.get(uuid);
    }

    @Override
    public void ensure(UUID uuid) {
        if (String.valueOf(this.name(uuid)).equals("null")) {
            LudwigLib.getInstance().getLogger().warning(uuid + " didn't have a cached name.");
        }
    }

    @Override
    public void update(UUID uuid, String name) {
        this.update0(uuid, name, true);
    }

    private void update0(final UUID uuid, final String name, boolean distributedToOthers) {
        uuidToName.put(uuid, name);
        for (Map.Entry<String, UUID> entry : new HashMap<>(nameToUuid).entrySet()) {
            if (!entry.getValue().equals(uuid)) continue;
            nameToUuid.remove(entry.getKey());
        }
        nameToUuid.put(name.toLowerCase(), uuid);
        if (distributedToOthers) {
            new BukkitRunnable() {
                public void run() {
                    LudwigLib.getInstance().runBackboneRedisCommand(redis -> {
                        redis.hset("UUIDCache", uuid.toString(), name);
                        return null;
                    });
                }
            }.runTaskAsynchronously(LudwigLib.getInstance());
            DistributedUUIDCacheUpdatePacket packet = new DistributedUUIDCacheUpdatePacket(uuid, name);
            LudwigXPacketHandler.sendToAll(packet);
        }
    }

    public static class DistributedUUIDCacheUpdatePacket implements XPacket {
        private UUID uuid;
        private String name;

        public DistributedUUIDCacheUpdatePacket() {
        }

        @Override
        public void onReceive() {
            if (LudwigUUIDCache.getImpl() instanceof DistributedUUIDCache) {
                ((DistributedUUIDCache) LudwigUUIDCache.getImpl()).update0(this.uuid, this.name, false);
            }
        }

        @ConstructorProperties(value = {"uuid", "name"})
        public DistributedUUIDCacheUpdatePacket(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public UUID getUuid() {
            return this.uuid;
        }

        public String getName() {
            return this.name;
        }
    }
}

