package com.noirm.arts;

import com.noirm.player.ShinobiData;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of {@link ArtsData} (sent inside {@code PowerPackets.SyncPowers}): shape stages
 * and progress, fusions, and the fusion trial, for the training screen, the workshop and the HUD.
 *
 * @param stages       stage per shape (ordinal order)
 * @param shares       progress of each shape's current stage, 0..1
 * @param fused        shape + nature fusions owned ({@link ArtsData#pair} keys)
 * @param pairs        fusions under way, and their progress (0..1), in step
 * @param trialNature  the nature being fused alone (ordinal), or -1
 * @param trialShare   progress of the trial's stage, 0..1
 * @param movingShape  the shape that moved at the last check, or -1 (also -1 for the trial)
 */
public record ArtsSync(int[] stages, float[] shares, int activeShape, List<Integer> fused, List<Integer> pairs, List<Float> pairShares,
                       int activePair, int trialNature, int trialStage, float trialShare, int trialFails, float trialProgress,
                       int movingShape, int movingPair, boolean movingTrial) {
    public static final ArtsSync EMPTY = new ArtsSync(new int[ChakraShape.values().length], new float[ChakraShape.values().length], -1,
            List.of(), List.of(), List.of(), -1, -1, 0, 0f, 0, 0f, -1, -1, false);

    public static ArtsSync of(ShinobiData data) {
        ArtsData arts = data.arts();
        int n = ChakraShape.values().length;
        int[] stages = new int[n];
        float[] shares = new float[n];
        for (ChakraShape shape : ChakraShape.values()) {
            stages[shape.ordinal()] = arts.stage(shape);
            shares[shape.ordinal()] = ShapeTraining.share(arts, shape);
        }
        List<Integer> pairs = new ArrayList<>();
        List<Float> pairShares = new ArrayList<>();
        arts.allPairProgress().forEach((p, s) -> {
            pairs.add(p);
            pairShares.add(Math.min(1f, s / ShapeTraining.FUSION_SECONDS));
        });
        return new ArtsSync(stages, shares, arts.activeShape() == null ? -1 : arts.activeShape().ordinal(), List.copyOf(arts.fusedPairs()),
                pairs, pairShares, arts.activePair(), arts.trialNature() == null ? -1 : arts.trialNature().ordinal(), arts.trialStage(),
                FusionTrial.share(arts), arts.trialFails(), arts.trialProgress(), arts.movingShape(), arts.movingPair(), arts.movingTrial());
    }

    public int stage(ChakraShape shape) {
        return shape.ordinal() < stages.length ? stages[shape.ordinal()] : 0;
    }

    public float share(ChakraShape shape) {
        return shape.ordinal() < shares.length ? shares[shape.ordinal()] : 0f;
    }

    public boolean fused(ChakraShape shape, com.noirm.clan.Nature nature) {
        return fused.contains(ArtsData.pair(shape, nature));
    }

    public float pairShare(int pair) {
        int i = pairs.indexOf(pair);
        return i < 0 ? 0f : pairShares.get(i);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(stages.length);
        for (int i = 0; i < stages.length; i++) {
            buf.writeByte(stages[i]);
            buf.writeFloat(shares[i]);
        }
        buf.writeVarInt(activeShape + 1);
        buf.writeCollection(fused, FriendlyByteBuf::writeVarInt);
        buf.writeCollection(pairs, FriendlyByteBuf::writeVarInt);
        buf.writeCollection(pairShares, FriendlyByteBuf::writeFloat);
        buf.writeVarInt(activePair + 1);
        buf.writeVarInt(trialNature + 1);
        buf.writeVarInt(trialStage);
        buf.writeFloat(trialShare);
        buf.writeVarInt(trialFails);
        buf.writeFloat(trialProgress);
        buf.writeVarInt(movingShape + 1);
        buf.writeVarInt(movingPair + 1);
        buf.writeBoolean(movingTrial);
    }

    public static ArtsSync decode(FriendlyByteBuf buf) {
        int n = Math.min(32, buf.readVarInt());
        int[] stages = new int[Math.max(n, ChakraShape.values().length)];
        float[] shares = new float[stages.length];
        for (int i = 0; i < n; i++) {
            stages[i] = buf.readByte();
            shares[i] = buf.readFloat();
        }
        int active = buf.readVarInt() - 1;
        List<Integer> fused = buf.readList(FriendlyByteBuf::readVarInt);
        List<Integer> pairs = buf.readList(FriendlyByteBuf::readVarInt);
        List<Float> pairShares = buf.readList(FriendlyByteBuf::readFloat);
        int activePair = buf.readVarInt() - 1;
        int trialNature = buf.readVarInt() - 1;
        int trialStage = buf.readVarInt();
        float trialShare = buf.readFloat();
        int fails = buf.readVarInt();
        float trialProgress = buf.readFloat();
        int movingShape = buf.readVarInt() - 1;
        int movingPair = buf.readVarInt() - 1;
        boolean movingTrial = buf.readBoolean();
        return new ArtsSync(stages, shares, active, fused, pairs, pairShares, activePair, trialNature, trialStage, trialShare, fails,
                trialProgress, movingShape, movingPair, movingTrial);
    }
}
