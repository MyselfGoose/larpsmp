package com.larpsmp.moneyevent.playerstate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * Versioned binary serialization for inventory slots and potion effects.
 *
 * <p>Item stacks use Paper {@link ItemStack#serializeAsBytes()} /
 * {@link ItemStack#deserializeBytes(byte[])}. Framing is independent so
 * repository tests can round-trip opaque blobs without Bukkit.
 */
public final class PlayerStateCodec {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int CODEC_VERSION = 1;
    private static final byte[] EMPTY_ITEM = new byte[0];

    private PlayerStateCodec() {
    }

    public static byte[] encodeItemArray(@Nullable ItemStack @Nullable [] items) throws IOException {
        ItemStack[] safe = items == null ? new ItemStack[0] : items;
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(CODEC_VERSION);
            out.writeInt(safe.length);
            for (ItemStack item : safe) {
                writeItem(out, item);
            }
        }
        return raw.toByteArray();
    }

    public static ItemStack[] decodeItemArray(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return new ItemStack[0];
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != CODEC_VERSION) {
                throw new IOException("Unsupported item codec version: " + version);
            }
            int length = in.readInt();
            if (length < 0 || length > 1024) {
                throw new IOException("Invalid item array length: " + length);
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = readItem(in);
            }
            return items;
        }
    }

    public static byte[] encodeSingleItem(@Nullable ItemStack item) throws IOException {
        return encodeItemArray(new ItemStack[] {item});
    }

    public static @Nullable ItemStack decodeSingleItem(byte[] data) throws IOException {
        ItemStack[] items = decodeItemArray(data);
        if (items.length == 0) {
            return null;
        }
        return items[0];
    }

    public static byte[] encodePotionEffects(Collection<PotionEffect> effects) throws IOException {
        Collection<PotionEffect> safe = effects == null ? List.of() : effects;
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(CODEC_VERSION);
            out.writeInt(safe.size());
            for (PotionEffect effect : safe) {
                writeUtf(out, effect.getType().getKey().asString());
                out.writeInt(effect.getDuration());
                out.writeInt(effect.getAmplifier());
                out.writeBoolean(effect.isAmbient());
                out.writeBoolean(effect.hasParticles());
                out.writeBoolean(effect.hasIcon());
            }
        }
        return raw.toByteArray();
    }

    public static List<PotionEffect> decodePotionEffects(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != CODEC_VERSION) {
                throw new IOException("Unsupported potion codec version: " + version);
            }
            int count = in.readInt();
            if (count < 0 || count > 256) {
                throw new IOException("Invalid potion effect count: " + count);
            }
            List<PotionEffect> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String key = readUtf(in);
                int duration = in.readInt();
                int amplifier = in.readInt();
                boolean ambient = in.readBoolean();
                boolean particles = in.readBoolean();
                boolean icon = in.readBoolean();
                NamespacedKey namespacedKey = NamespacedKey.fromString(key);
                if (namespacedKey == null) {
                    continue;
                }
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(namespacedKey);
                if (type == null) {
                    continue;
                }
                effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
            return effects;
        }
    }

    /**
     * Packs length-prefixed opaque slot payloads (for unit tests without Bukkit ItemStack).
     */
    public static byte[] encodeOpaqueSlots(byte[]... slots) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(CODEC_VERSION);
            out.writeInt(slots.length);
            for (byte[] slot : slots) {
                byte[] payload = slot == null ? EMPTY_ITEM : slot;
                out.writeInt(payload.length);
                out.write(payload);
            }
        }
        return raw.toByteArray();
    }

    public static byte[][] decodeOpaqueSlots(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return new byte[0][];
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int version = in.readInt();
            if (version != CODEC_VERSION) {
                throw new IOException("Unsupported opaque codec version: " + version);
            }
            int length = in.readInt();
            if (length < 0 || length > 1024) {
                throw new IOException("Invalid opaque array length: " + length);
            }
            byte[][] slots = new byte[length][];
            for (int i = 0; i < length; i++) {
                int size = in.readInt();
                if (size < 0 || size > 1_048_576) {
                    throw new IOException("Invalid opaque slot size: " + size);
                }
                byte[] payload = new byte[size];
                if (size > 0) {
                    in.readFully(payload);
                }
                slots[i] = payload;
            }
            return slots;
        }
    }

    /**
     * Empty framed slot array that does not require a live Bukkit server (safe during auth preload).
     */
    public static byte[] emptyItemArray(int size) throws IOException {
        int safe = Math.max(0, size);
        byte[][] slots = new byte[safe][];
        for (int i = 0; i < safe; i++) {
            slots[i] = EMPTY_ITEM;
        }
        return encodeOpaqueSlots(slots);
    }

    /**
     * Empty potion-effect blob that does not require Bukkit potion types.
     */
    public static byte[] emptyEffects() throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(CODEC_VERSION);
            out.writeInt(0);
        }
        return raw.toByteArray();
    }

    private static void writeItem(DataOutputStream out, @Nullable ItemStack item) throws IOException {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            out.writeInt(0);
            return;
        }
        byte[] encoded = item.serializeAsBytes();
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static @Nullable ItemStack readItem(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 1_048_576) {
            throw new IOException("Invalid item payload size: " + size);
        }
        if (size == 0) {
            return null;
        }
        byte[] payload = new byte[size];
        in.readFully(payload);
        return ItemStack.deserializeBytes(payload);
    }

    private static void writeUtf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > 4096) {
            throw new IOException("Invalid utf length: " + size);
        }
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
