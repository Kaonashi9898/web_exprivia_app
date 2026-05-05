import { Injectable } from '@angular/core';
import { Helper, colors, DxfEntity } from 'dxf';

export interface DxfConversionResult {
  dataUrl: string;
  svgText: string;
  naturalWidth: number;
  naturalHeight: number;
}

@Injectable({ providedIn: 'root' })
export class DxfConverterService {
  convert(dxfText: string): DxfConversionResult {
    const helper = new Helper(dxfText);
    let svgText = helper.toSVG();

    if (!svgText?.trim()) {
      throw new Error('Il file DXF non contiene elementi disegnabili.');
    }

    const viewBoxMatch = svgText.match(/viewBox="([^"]+)"/);
    let naturalWidth = 1000;
    let naturalHeight = 1000;

    if (viewBoxMatch) {
      const parts = viewBoxMatch[1].trim().split(/\s+/).map(Number);
      if (parts.length === 4 && parts[2] > 0 && parts[3] > 0) {
        naturalWidth = Math.round(parts[2]);
        naturalHeight = Math.round(parts[3]);
      }
    }

    svgText = svgText
      .replace(/width="[^"]*"/, `width="${naturalWidth}"`)
      .replace(/height="[^"]*"/, `height="${naturalHeight}"`);

    const layers = helper.parsed?.tables?.layers ?? {};
    const textElements = helper.denormalised
      .filter((entity) => entity.type === 'TEXT' || entity.type === 'MTEXT')
      .map((entity) => this.entityToSvgText(entity, layers))
      .filter((element): element is string => element !== null);

    if (textElements.length > 0) {
      svgText = svgText.replace('</svg>', `${textElements.join('\n')}\n</svg>`);
    }

    const dataUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgText)}`;
    return { dataUrl, svgText, naturalWidth, naturalHeight };
  }

  private entityToSvgText(
    entity: DxfEntity,
    layers: Record<string, { colorNumber: number }>,
  ): string | null {
    const rawText = entity.string ?? '';
    const text = entity.type === 'MTEXT' ? this.stripMTextFormatting(rawText) : rawText.trim();
    if (!text) {
      return null;
    }

    const x = entity.x ?? 0;
    const y = -(entity.y ?? 0);
    const fontSize = (entity.textHeight ?? entity.nominalTextHeight ?? 2.5).toFixed(4);
    const anchor = this.textAnchor(entity.hAlign as number | undefined);
    const fill = this.resolveColor(entity, layers);
    const escaped = this.escapeXml(text);

    return (
      `  <text x="${x}" y="${y}" font-size="${fontSize}" font-family="sans-serif"` +
      ` fill="${fill}" stroke="none" text-anchor="${anchor}" dominant-baseline="auto">${escaped}</text>`
    );
  }

  private stripMTextFormatting(value: string): string {
    return value
      .replace(/\\P/g, ' ')
      .replace(/\\~/g, '\u00a0')
      .replace(/\{\\[A-Za-z][^;]*;([^}]*)\}/g, '$1')
      .replace(/[{}]/g, '')
      .replace(/\\[A-Za-z]/g, '')
      .replace(/%%c/gi, '\u00d8')
      .replace(/%%d/gi, '\u00b0')
      .replace(/%%p/gi, '\u00b1')
      .trim();
  }

  private textAnchor(hAlign: number | undefined): string {
    if (hAlign === 1) {
      return 'middle';
    }
    if (hAlign === 2) {
      return 'end';
    }
    return 'start';
  }

  private resolveColor(
    entity: DxfEntity,
    layers: Record<string, { colorNumber: number }>,
  ): string {
    const entityColor =
      typeof entity.colorNumber === 'number' && entity.colorNumber !== 256
        ? entity.colorNumber
        : null;
    const layerColor = entity.layer && layers[entity.layer] ? layers[entity.layer].colorNumber : null;
    const colorIndex = entityColor ?? layerColor ?? 7;

    if (colorIndex === 7) {
      return '#000000';
    }

    const rgb = colors[colorIndex];
    return rgb ? `rgb(${rgb[0]},${rgb[1]},${rgb[2]})` : '#000000';
  }

  private escapeXml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');
  }
}
