import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  ViewChild,
  effect,
  inject,
  NgZone,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FloorPlanService } from '../services/floor-plan';
import { RoomMarkerComponent, MarkerDragStart } from '../markers/room-marker';
import { StationMarkerComponent } from '../markers/station-marker';

interface DragState {
  type: 'room' | 'meeting' | 'station';
  id: string;
  startMouseX: number;
  startMouseY: number;
  startXPct: number;
  startYPct: number;
}

interface ImageFrame {
  left: number;
  top: number;
  width: number;
  height: number;
}

interface ViewState {
  zoom: number;
  pan: { x: number; y: number };
}

@Component({
  selector: 'app-floor-canvas',
  standalone: true,
  imports: [CommonModule, RoomMarkerComponent, StationMarkerComponent],
  templateUrl: './floor-canvas.html',
  styleUrls: ['./floor-canvas.scss'],
})
export class FloorCanvasComponent implements AfterViewInit, OnDestroy {
  protected readonly fps = inject(FloorPlanService);
  private readonly elRef = inject(ElementRef<HTMLElement>);
  private readonly ngZone = inject(NgZone);

  @ViewChild('sceneEl') sceneEl!: ElementRef<HTMLElement>;
  @ViewChild('markersOverlayEl') private markersOverlayEl?: ElementRef<HTMLElement>;

  protected imageFrame: ImageFrame = { left: 0, top: 0, width: 0, height: 0 };

  private dragState: DragState | null = null;

  private isPanning = false;
  private panMoved = false;
  private panStartX = 0;
  private panStartY = 0;
  private panOriginX = 0;
  private panOriginY = 0;
  private pendingPan: { x: number; y: number } | null = null;

  private previewState: ViewState | null = null;
  private previewRenderRafId: number | null = null;
  private previewClearRafId: number | null = null;
  private wheelCommitTimerId: number | null = null;

  private readonly wheelHandler = (event: WheelEvent) => this.onWheel(event);
  private readonly mouseMoveHandler = (event: MouseEvent) => this.onMouseMoveOutside(event);
  private readonly mouseUpHandler = () => this.onMouseUpOutside();

  constructor() {
    effect(() => {
      const { x, y } = this.fps.pan();
      const zoom = this.fps.zoom();
      this.writeSceneTransform(x, y, zoom);
    });

    effect(() => {
      this.fps.image();
      this.updateImageFrame();
    });
  }

  ngAfterViewInit(): void {
    this.elRef.nativeElement.addEventListener('wheel', this.wheelHandler, { passive: false });

    this.ngZone.runOutsideAngular(() => {
      document.addEventListener('mousemove', this.mouseMoveHandler);
      document.addEventListener('mouseup', this.mouseUpHandler);
    });

    this.updateImageFrame();
  }

  ngOnDestroy(): void {
    this.elRef.nativeElement.removeEventListener('wheel', this.wheelHandler);
    document.removeEventListener('mousemove', this.mouseMoveHandler);
    document.removeEventListener('mouseup', this.mouseUpHandler);
    this.clearWheelCommitTimer();
    this.cancelFrame(this.previewRenderRafId);
    this.cancelFrame(this.previewClearRafId);
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.updateImageFrame();
    if (this.previewState) {
      this.schedulePreviewRender();
    }
  }

  private onWheel(event: WheelEvent): void {
    event.preventDefault();
    const rect = this.hostRect();
    const focalX = event.clientX - rect.left;
    const focalY = event.clientY - rect.top;
    const currentState = this.previewState ?? {
      zoom: this.fps.zoom(),
      pan: this.fps.pan(),
    };

    this.previewState = applyZoomToState(
      currentState.zoom,
      currentState.pan,
      wheelZoomFactor(event),
      focalX,
      focalY,
    );

    this.schedulePreviewRender();
    this.scheduleWheelCommit();
  }

  zoomIn(): void {
    this.flushWheelZoom();
    const { width, height } = this.hostRect();
    this.fps.applyZoom(1.25, width / 2, height / 2);
  }

  zoomOut(): void {
    this.flushWheelZoom();
    const { width, height } = this.hostRect();
    this.fps.applyZoom(0.8, width / 2, height / 2);
  }

  toScreenX(xPct: number): number {
    const { x: panX } = this.fps.pan();
    const zoom = this.fps.zoom();
    const { left, width } = this.imageFrame;
    return panX + zoom * (left + (xPct / 100) * width);
  }

  toScreenY(yPct: number): number {
    const { y: panY } = this.fps.pan();
    const zoom = this.fps.zoom();
    const { top, height } = this.imageFrame;
    return panY + zoom * (top + (yPct / 100) * height);
  }

  @HostListener('mousedown', ['$event'])
  onMouseDown(event: MouseEvent): void {
    this.flushWheelZoom();

    if (event.button !== 0) return;
    const target = event.target as HTMLElement;
    if (target.closest('app-room-marker, app-station-marker')) return;

    this.isPanning = true;
    this.panMoved = false;
    this.panStartX = event.clientX;
    this.panStartY = event.clientY;
    const { x, y } = this.fps.pan();
    this.panOriginX = x;
    this.panOriginY = y;
    this.pendingPan = null;
    this.elRef.nativeElement.style.cursor = 'grabbing';
    event.preventDefault();
  }

  private onMouseMoveOutside(event: MouseEvent): void {
    if (this.isPanning) {
      const dx = event.clientX - this.panStartX;
      const dy = event.clientY - this.panStartY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) this.panMoved = true;

      if (this.panMoved) {
        const x = this.panOriginX + dx;
        const y = this.panOriginY + dy;
        this.writeSceneTransform(x, y, this.fps.zoom());
        this.pendingPan = { x, y };

        const overlayEl = this.markersOverlayEl?.nativeElement;
        if (overlayEl) overlayEl.style.transform = `translate(${dx}px, ${dy}px)`;
      }
      return;
    }

    if (!this.dragState) return;

    this.ngZone.run(() => {
      if (!this.dragState) return;
      const zoom = this.fps.zoom();
      const { width, height } = this.imageFrame;
      if (!width || !height) return;

      const dx = ((event.clientX - this.dragState.startMouseX) / zoom / width) * 100;
      const dy = ((event.clientY - this.dragState.startMouseY) / zoom / height) * 100;
      const xPct = clamp(this.dragState.startXPct + dx);
      const yPct = clamp(this.dragState.startYPct + dy);

      if (this.dragState.type === 'room') {
        this.fps.updateRoom(this.dragState.id, { xPct, yPct });
      } else if (this.dragState.type === 'meeting') {
        this.fps.updateMeeting(this.dragState.id, { xPct, yPct });
      } else {
        this.fps.updateStation(this.dragState.id, { xPct, yPct });
      }
    });
  }

  private onMouseUpOutside(): void {
    const wasPanning = this.isPanning;
    this.isPanning = false;
    this.dragState = null;

    if (wasPanning && this.pendingPan) {
      const { x, y } = this.pendingPan;
      this.pendingPan = null;
      const overlayEl = this.markersOverlayEl?.nativeElement;
      if (overlayEl) overlayEl.style.transform = '';
      this.ngZone.run(() => this.fps.setPan(x, y));
    }

    const mode = this.fps.mode();
    this.elRef.nativeElement.style.cursor = mode === 'view' ? 'grab' : 'crosshair';
  }

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    this.flushWheelZoom();

    if (this.panMoved || !this.fps.image()) return;
    const target = event.target as HTMLElement;
    if (target.closest('app-room-marker, app-station-marker')) return;

    const point = this.toImagePct(event.clientX, event.clientY);
    if (!point) return;
    const mode = this.fps.mode();

    if (mode === 'placing-room') {
      this.fps.addRoom(point.x, point.y);
    } else if (mode === 'placing-meeting') {
      this.fps.addMeeting(point.x, point.y);
    } else if (mode === 'placing-station') {
      const roomId = this.fps.pendingRoomId();
      if (roomId) {
        this.fps.addStation(point.x, point.y, roomId);
      }
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.fps.setMode('view');
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.fps.loadImage(file);
  }

  onDrop(event: DragEvent): void {
    const file = event.dataTransfer?.files?.[0];
    if (file) this.fps.loadImage(file);
  }

  onRoomDragStart(event: MarkerDragStart): void {
    this.flushWheelZoom();
    const room = this.fps.rooms().find((r) => r.id === event.id);
    if (!room) return;

    this.dragState = {
      type: 'room',
      id: event.id,
      startMouseX: event.mouseX,
      startMouseY: event.mouseY,
      startXPct: room.xPct,
      startYPct: room.yPct,
    };
  }

  onMeetingDragStart(event: MarkerDragStart): void {
    this.flushWheelZoom();
    const meeting = this.fps.meetings().find((m) => m.id === event.id);
    if (!meeting) return;

    this.dragState = {
      type: 'meeting',
      id: event.id,
      startMouseX: event.mouseX,
      startMouseY: event.mouseY,
      startXPct: meeting.xPct,
      startYPct: meeting.yPct,
    };
  }

  onStationDragStart(event: MarkerDragStart): void {
    this.flushWheelZoom();
    const station = this.fps.stations().find((s) => s.id === event.id);
    if (!station) return;

    this.dragState = {
      type: 'station',
      id: event.id,
      startMouseX: event.mouseX,
      startMouseY: event.mouseY,
      startXPct: station.xPct,
      startYPct: station.yPct,
    };
  }

  onAddStation(roomId: string): void {
    this.flushWheelZoom();
    this.fps.setMode('placing-station', roomId);
    this.elRef.nativeElement.style.cursor = 'crosshair';
  }

  private schedulePreviewRender(): void {
    if (this.previewRenderRafId !== null) return;

    this.previewRenderRafId = requestAnimationFrame(() => {
      this.previewRenderRafId = null;

      if (!this.previewState) return;
      this.writeSceneTransform(
        this.previewState.pan.x,
        this.previewState.pan.y,
        this.previewState.zoom,
      );
      this.writeOverlayPreviewTransform(this.previewState);
    });
  }

  private scheduleWheelCommit(): void {
    this.clearWheelCommitTimer();
    this.wheelCommitTimerId = window.setTimeout(() => this.flushWheelZoom(), 80);
  }

  private flushWheelZoom(): void {
    if (!this.previewState) return;

    const { zoom, pan } = this.previewState;
    this.previewState = null;
    this.clearWheelCommitTimer();
    this.cancelFrame(this.previewRenderRafId);
    this.previewRenderRafId = null;

    this.ngZone.run(() => this.fps.setView(zoom, pan.x, pan.y));

    this.cancelFrame(this.previewClearRafId);
    this.previewClearRafId = requestAnimationFrame(() => {
      this.previewClearRafId = null;
      this.clearOverlayPreviewTransform();
    });
  }

  private clearWheelCommitTimer(): void {
    if (this.wheelCommitTimerId !== null) {
      window.clearTimeout(this.wheelCommitTimerId);
      this.wheelCommitTimerId = null;
    }
  }

  private cancelFrame(id: number | null): void {
    if (id !== null) {
      cancelAnimationFrame(id);
    }
  }

  private writeSceneTransform(x: number, y: number, zoom: number): void {
    const el = this.sceneEl?.nativeElement;
    if (el) {
      el.style.transform = `translate(${x}px, ${y}px) scale(${zoom})`;
    }
  }

  private writeOverlayPreviewTransform(state: ViewState): void {
    const overlayEl = this.markersOverlayEl?.nativeElement;
    if (!overlayEl) return;

    const baseZoom = this.fps.zoom();
    const basePan = this.fps.pan();
    const scale = state.zoom / baseZoom;
    const x = state.pan.x - scale * basePan.x;
    const y = state.pan.y - scale * basePan.y;
    overlayEl.style.transform = `translate(${x}px, ${y}px) scale(${scale})`;
  }

  private clearOverlayPreviewTransform(): void {
    const overlayEl = this.markersOverlayEl?.nativeElement;
    if (overlayEl) {
      overlayEl.style.transform = '';
    }
  }

  private hostRect() {
    return this.elRef.nativeElement.getBoundingClientRect();
  }

  private updateImageFrame(): void {
    const image = this.fps.image();
    const host = this.elRef.nativeElement;
    const hostWidth = host.clientWidth;
    const hostHeight = host.clientHeight;

    if (!image || !hostWidth || !hostHeight || !image.naturalWidth || !image.naturalHeight) {
      this.imageFrame = { left: 0, top: 0, width: 0, height: 0 };
      return;
    }

    const scale = Math.min(hostWidth / image.naturalWidth, hostHeight / image.naturalHeight);
    const width = image.naturalWidth * scale;
    const height = image.naturalHeight * scale;

    this.imageFrame = {
      left: (hostWidth - width) / 2,
      top: (hostHeight - height) / 2,
      width,
      height,
    };
  }

  private toImagePct(clientX: number, clientY: number): { x: number; y: number } | null {
    const rect = this.hostRect();
    const { x: panX, y: panY } = this.fps.pan();
    const zoom = this.fps.zoom();
    const { left, top, width, height } = this.imageFrame;
    if (!width || !height) return null;

    const sceneX = (clientX - rect.left - panX) / zoom;
    const sceneY = (clientY - rect.top - panY) / zoom;
    const xPct = ((sceneX - left) / width) * 100;
    const yPct = ((sceneY - top) / height) * 100;

    if (xPct < 0 || xPct > 100 || yPct < 0 || yPct > 100) return null;

    return {
      x: xPct,
      y: yPct,
    };
  }
}

function applyZoomToState(
  currentZoom: number,
  currentPan: { x: number; y: number },
  factor: number,
  focalX: number,
  focalY: number,
): ViewState {
  const nextZoom = clamp(currentZoom * factor, 0.2, 8);
  const ratio = nextZoom / currentZoom;

  return {
    zoom: nextZoom,
    pan: {
      x: focalX - (focalX - currentPan.x) * ratio,
      y: focalY - (focalY - currentPan.y) * ratio,
    },
  };
}

function wheelZoomFactor(event: WheelEvent): number {
  const delta = normalizeWheelDelta(event);
  return clamp(Math.exp(-delta * 0.0015), 0.85, 1.15);
}

function normalizeWheelDelta(event: WheelEvent): number {
  switch (event.deltaMode) {
    case WheelEvent.DOM_DELTA_LINE:
      return event.deltaY * 16;
    case WheelEvent.DOM_DELTA_PAGE:
      return event.deltaY * 160;
    default:
      return event.deltaY;
  }
}

function clamp(v: number, min = 0, max = 100): number {
  return Math.max(min, Math.min(max, v));
}
