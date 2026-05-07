export interface PlanPosition {
  xPct: number;
  yPct: number;
}

export interface RoomZoomInput {
  roomPosition: PlanPosition;
  stationPositions: PlanPosition[];
  viewportWidth: number;
  viewportHeight: number;
  stageWidth: number;
  stageHeight: number;
}

export function roomZoomStyle(input: RoomZoomInput): Record<string, string> {
  if (
    input.viewportWidth <= 0
    || input.viewportHeight <= 0
    || input.stageWidth <= 0
    || input.stageHeight <= 0
  ) {
    return {};
  }

  const box = roomBoundingBox([input.roomPosition, ...input.stationPositions]);
  const boxWidth = Math.max((box.maxX - box.minX) / 100 * input.stageWidth, 180);
  const boxHeight = Math.max((box.maxY - box.minY) / 100 * input.stageHeight, 140);
  const padding = Math.max(24, Math.min(input.viewportWidth, input.viewportHeight) * 0.045);
  const scale = clamp(
    Math.min(
      input.viewportWidth / (boxWidth + padding * 2),
      input.viewportHeight / (boxHeight + padding * 2),
    ),
    2.35,
    7.2,
  );

  const centerX = input.roomPosition.xPct / 100 * input.stageWidth;
  const centerY = input.roomPosition.yPct / 100 * input.stageHeight;
  const translateX = input.viewportWidth / 2 - centerX * scale;
  const translateY = input.viewportHeight / 2 - centerY * scale;
  const counterScale = clamp(1 / scale, 0.24, 0.72);

  return {
    transform: `translate(${translateX}px, ${translateY}px) scale(${scale})`,
    transformOrigin: '0 0',
    '--station-pin-counter-scale': String(counterScale),
  };
}

function roomBoundingBox(positions: PlanPosition[]) {
  const xs = positions.map((position) => position.xPct);
  const ys = positions.map((position) => position.yPct);
  let minX = Math.min(...xs);
  let maxX = Math.max(...xs);
  let minY = Math.min(...ys);
  let maxY = Math.max(...ys);

  const minSpanPct = 9;
  if (maxX - minX < minSpanPct) {
    const center = (minX + maxX) / 2;
    minX = center - minSpanPct / 2;
    maxX = center + minSpanPct / 2;
  }
  if (maxY - minY < minSpanPct) {
    const center = (minY + maxY) / 2;
    minY = center - minSpanPct / 2;
    maxY = center + minSpanPct / 2;
  }

  return {
    minX: clamp(minX, 0, 100),
    maxX: clamp(maxX, 0, 100),
    minY: clamp(minY, 0, 100),
    maxY: clamp(maxY, 0, 100),
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
