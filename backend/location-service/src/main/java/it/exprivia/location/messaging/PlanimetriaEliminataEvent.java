package it.exprivia.location.messaging;

import java.util.List;

public record PlanimetriaEliminataEvent(Long pianoId, List<Long> postazioneIds) {}
