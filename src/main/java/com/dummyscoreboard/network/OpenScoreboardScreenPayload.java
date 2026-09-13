package com.dummyscoreboard.network;

import com.dummyscoreboard.DummyScoreboardMod;
import com.dummyscoreboard.rank.LeaderboardEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Sent to a player when they right-click a Scoreboard Dummy. There's no slotted container involved,
 * so this skips the AbstractContainerMenu machinery entirely - the client just opens a plain Screen
 * with both boards already attached, so switching the Local/Global toggle needs no extra round trip.
 */
public record OpenScoreboardScreenPayload(
        int dummyEntityId,
        List<LeaderboardEntry> localEntries,
        List<LeaderboardEntry> globalEntries,
        boolean globalAvailable,
        String modpackDisplayName
) implements CustomPacketPayload {

    public static final Type<OpenScoreboardScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(DummyScoreboardMod.MODID, "open_scoreboard_screen"));

    public static final StreamCodec<ByteBuf, OpenScoreboardScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenScoreboardScreenPayload::dummyEntityId,
            LeaderboardEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenScoreboardScreenPayload::localEntries,
            LeaderboardEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenScoreboardScreenPayload::globalEntries,
            ByteBufCodecs.BOOL, OpenScoreboardScreenPayload::globalAvailable,
            ByteBufCodecs.STRING_UTF8, OpenScoreboardScreenPayload::modpackDisplayName,
            OpenScoreboardScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
