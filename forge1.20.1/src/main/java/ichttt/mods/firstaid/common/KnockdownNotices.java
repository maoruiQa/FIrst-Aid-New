/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.common.network.MessageKnockdownNotice;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which knockdown settings each player has acknowledged, so the full-screen notice is shown on first join and
 * again only after an administrator changes the settings. Stored per world in {@code firstaid/knockdown_notice.json}.
 */
public final class KnockdownNotices {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "knockdown_notice.json";
    private static final Map<UUID, String> ACKNOWLEDGED = new ConcurrentHashMap<>();
    @Nullable
    private static Path storageFile;
    @Nullable
    private static String lastAnnouncedFingerprint;

    private KnockdownNotices() {
    }

    public static void load(MinecraftServer server) {
        ACKNOWLEDGED.clear();
        storageFile = server.getWorldPath(LevelResource.ROOT).resolve(FirstAid.MODID).resolve(FILE_NAME).normalize();
        lastAnnouncedFingerprint = KnockdownRules.fingerprint();
        if (!Files.isRegularFile(storageFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonObject players = root == null ? null : root.getAsJsonObject("acknowledged");
            if (players == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                try {
                    ACKNOWLEDGED.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
                    FirstAid.LOGGER.warn("Ignoring malformed knockdown notice entry {} in {}", entry.getKey(), storageFile);
                }
            }
        } catch (IOException | JsonParseException | ClassCastException | IllegalStateException e) {
            FirstAid.LOGGER.warn("Failed to read {}, knockdown notices will be shown again", storageFile, e);
        }
    }

    public static void unload() {
        ACKNOWLEDGED.clear();
        storageFile = null;
        lastAnnouncedFingerprint = null;
    }

    public static void onLogin(ServerPlayer player) {
        if (!KnockdownRules.fingerprint().equals(ACKNOWLEDGED.get(player.getUUID()))) {
            send(player);
        }
    }

    /** Sends the notice to every online player if the knockdown settings differ from the last announced ones. */
    public static void broadcastIfChanged(MinecraftServer server) {
        String fingerprint = KnockdownRules.fingerprint();
        if (fingerprint.equals(lastAnnouncedFingerprint)) {
            return;
        }
        lastAnnouncedFingerprint = fingerprint;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    public static void acknowledge(ServerPlayer player) {
        String fingerprint = KnockdownRules.fingerprint();
        if (fingerprint.equals(ACKNOWLEDGED.put(player.getUUID(), fingerprint))) {
            return;
        }
        save();
    }

    private static void send(ServerPlayer player) {
        FirstAid.NETWORKING.send(PacketDistributor.PLAYER.with(() -> player), MessageKnockdownNotice.current());
    }

    private static void save() {
        Path file = storageFile;
        if (file == null) {
            return;
        }
        JsonObject players = new JsonObject();
        for (Map.Entry<UUID, String> entry : new TreeMap<>(ACKNOWLEDGED).entrySet()) {
            players.addProperty(entry.getKey().toString(), entry.getValue());
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("acknowledged", players);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            FirstAid.LOGGER.error("Failed to save knockdown notice acknowledgements to {}", file, e);
        }
    }
}
