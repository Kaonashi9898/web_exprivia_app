declare module 'dxf' {
  export const colors: Record<number, [number, number, number]>;

  export class Helper {
    constructor(dxfString: string);

    readonly parsed: {
      entities: DxfEntity[];
      tables?: {
        layers?: Record<string, { colorNumber: number }>;
      };
    };

    readonly denormalised: DxfEntity[];

    toSVG(): string;
  }

  export interface DxfEntity {
    type: string;
    layer?: string;
    colorNumber?: number;
    transforms?: unknown[];
    string?: string;
    x?: number;
    y?: number;
    textHeight?: number;
    hAlign?: number;
    nominalTextHeight?: number;
    [key: string]: unknown;
  }
}
