import { describe, expect, it } from 'vitest';
import { roomZoom } from './plan-zoom.utils';

describe('roomZoom', () => {
  it('should center the bounding box of the room and its stations', () => {
    const zoom = roomZoom({
      roomPosition: { xPct: 20, yPct: 20 },
      stationPositions: [
        { xPct: 60, yPct: 50 },
        { xPct: 55, yPct: 45 },
      ],
      viewportWidth: 1000,
      viewportHeight: 800,
      stageWidth: 1000,
      stageHeight: 800,
    });

    expect(zoom).not.toBeNull();
    expect(zoom!.translateX).toBeCloseTo(500 - 400 * zoom!.scale, 5);
    expect(zoom!.translateY).toBeCloseTo(400 - 280 * zoom!.scale, 5);
  });
});
