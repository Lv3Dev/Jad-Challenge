package com.jadchallenge;

import lombok.RequiredArgsConstructor;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;

@RequiredArgsConstructor
class QueuedAttack
{
	final Prayer prayer;
	final NPC npc;
	final int startTick;
}
