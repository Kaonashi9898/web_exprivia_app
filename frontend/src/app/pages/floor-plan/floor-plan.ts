import { ChangeDetectorRef, Component, ElementRef, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, map, of, Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, PlanimetriaLayout, PlanimetriaResponse, Sede } from '../../core/app.models';
import { apiErrorMessage, apiErrorStatus } from '../../core/api-error.utils';
import { environment } from '../../../environments/environment';
import { DxfConverterService } from '../../core/dxf-converter.service';
import { roomZoom, roomZoomStyle, RoomZoomInput } from '../../core/plan-zoom.utils';

const EXPRIVIA_ITALIA_SEDI: Sede[] = [];

type LayoutRoom = NonNullable<PlanimetriaLayout['rooms']>[number];
type LayoutMeeting = NonNullable<PlanimetriaLayout['meetings']>[number];
type DisplayRoom = LayoutRoom | LayoutMeeting;
type PositionedRoom = DisplayRoom & { position: { xPct: number; yPct: number } };
type LayoutStation = NonNullable<PlanimetriaLayout['stations']>[number];
type PositionedStation = LayoutStation & { position: { xPct: number; yPct: number } };

@Component({
  selector: 'app-floor-plan',
  imports: [FormsModule, NgStyle],
  templateUrl: './floor-plan.html',
  styleUrl: './floor-plan.css',
})
export class FloorPlanComponent implements OnInit, OnDestroy {
  @ViewChild('planPreview') private planPreview?: ElementRef<HTMLElement>;
  @ViewChild('planStage') private planStage?: ElementRef<HTMLElement>;

  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly dxfConverter = inject(DxfConverterService);
  private sediSubscription: Subscription | null = null;
  private edificiSubscription: Subscription | null = null;
  private pianiSubscription: Subscription | null = null;
  private uploadSubscription: Subscription | null = null;
  private importSubscription: Subscription | null = null;
  private deleteSubscription: Subscription | null = null;
  private saveSubscription: Subscription | null = null;
  private planimetriaSubscription: Subscription | null = null;
  private imageSubscription: Subscription | null = null;
  private layoutSubscription: Subscription | null = null;
  private planStatusSubscription: Subscription | null = null;
  private currentPlanRequestId = 0;

  sedi: Sede[] = EXPRIVIA_ITALIA_SEDI;
  edifici: Edificio[] = [];
  allPiani: Piano[] = [];
  piani: Piano[] = [];
  selectedSedeId: number | null = null;
  selectedEdificioId: number | null = null;
  selectedPianoId: number | null = null;
  selectedRoomId: string | null = null;
  pianiConPlanimetria = new Map<number, boolean>();

  planimetria: PlanimetriaResponse | null = null;
  layout: PlanimetriaLayout | null = null;
  imageSrc = '';
  imageFile: File | null = null;
  imagePreviewFile: File | null = null;
  jsonFile: File | null = null;
  message = '';
  error = '';
  sediLoading = false;
  pianiLoading = false;
  readonly editorUrl = environment.floorPlanEditorUrl;

  ngOnInit(): void {
    this.sediLoading = true;
    this.sediSubscription?.unsubscribe();
    this.sediSubscription = this.api.listSedi().subscribe({
      next: (sedi) => {
        this.sedi = sedi.length ? sedi : EXPRIVIA_ITALIA_SEDI;
        this.sediLoading = false;
        this.refreshView();
      },
      error: (err) => {
        this.sediLoading = false;
        this.error = apiErrorMessage(err, 'Impossibile caricare le sedi.');
        this.refreshView();
      },
    });
  }

  ngOnDestroy(): void {
    this.sediSubscription?.unsubscribe();
    this.edificiSubscription?.unsubscribe();
    this.pianiSubscription?.unsubscribe();
    this.uploadSubscription?.unsubscribe();
    this.importSubscription?.unsubscribe();
    this.deleteSubscription?.unsubscribe();
    this.saveSubscription?.unsubscribe();
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();
    this.planStatusSubscription?.unsubscribe();
    this.revokeImageUrl();
  }

  onSedeChange(): void {
    this.edifici = [];
    this.allPiani = [];
    this.piani = [];
    this.selectedEdificioId = null;
    this.selectedPianoId = null;
    this.clearPlan();
    if (!this.selectedSedeId) return;
    this.edificiSubscription?.unsubscribe();
    this.edificiSubscription = this.api.listEdifici(this.selectedSedeId).subscribe({
      next: (edifici) => {
        this.edifici = edifici;
        this.pianiLoading = false;
        this.refreshView();
      },
      error: (err) => {
        this.pianiLoading = false;
        this.error = apiErrorMessage(err, 'Impossibile caricare gli edifici della sede.');
        this.refreshView();
      },
    });
  }

  onEdificioChange(): void {
    this.allPiani = [];
    this.piani = [];
    this.selectedPianoId = null;
    this.clearPlan();
    if (!this.selectedEdificioId) {
      this.refreshView();
      return;
    }
    this.pianiLoading = true;
    this.pianiSubscription?.unsubscribe();
    this.pianiSubscription = this.api.listPiani(this.selectedEdificioId).subscribe({
      next: (piani) => {
        this.allPiani = piani;
        this.piani = piani;
        this.pianiLoading = false;
        this.syncPlanStatus(this.piani);
      },
      error: (err) => {
        this.pianiLoading = false;
        this.error = apiErrorMessage(err, 'Impossibile caricare i piani della sede.');
        this.refreshView();
      },
    });
  }

  onPianoChange(): void {
    this.clearPlan();
    if (this.selectedPianoId) {
      this.loadPlan();
    }
  }

  setImageFile(event: Event): void {
    this.imageFile = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.imagePreviewFile = null;
    this.refreshView();
  }

  setJsonFile(event: Event): void {
    this.jsonFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  uploadImage(): void {
    if (!this.selectedPianoId || !this.imageFile) {
      return;
    }

    this.clearMessages();
    this.revokeImageUrl();
    this.imagePreviewFile = null;

    if (this.isDxfFile(this.imageFile)) {
      this.loadDxfPreview(this.imageFile);
      return;
    }

    if (this.isDwgFile(this.imageFile)) {
      this.error = "L'anteprima automatica e disponibile per DXF. Carica un SVG, PNG o JPG per visualizzare un DWG.";
      this.refreshView();
      return;
    }

    this.imageSrc = URL.createObjectURL(this.imageFile);
    this.message = 'Planimetria caricata in anteprima. Premi "Salva planimetria" per salvarla nel database.';
    this.refreshView();
  }

  importJson(): void {
    if (!this.selectedPianoId || !this.jsonFile) {
      return;
    }

    if (!this.hasImageLoaded()) {
      this.error = "Carica prima l'immagine della planimetria per il piano selezionato.";
      this.message = '';
      this.refreshView();
      return;
    }

    this.clearMessages();
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsedLayout = JSON.parse(String(reader.result ?? '')) as PlanimetriaLayout;
        this.layout = this.normalizeLayout(parsedLayout);
        this.selectedRoomId = null;
        this.message = 'Layout caricato in anteprima. Premi "Salva planimetria" per salvarlo nel database.';
        this.refreshView();
      } catch {
        this.error = 'Import JSON non riuscito.';
        this.refreshView();
      }
    };
    reader.onerror = () => {
      this.error = 'Import JSON non riuscito.';
      this.refreshView();
    };
    reader.readAsText(this.jsonFile);
  }

  savePlan(): void {
    if (!this.selectedPianoId) {
      this.error = 'Seleziona un piano prima di salvare.';
      this.message = '';
      this.refreshView();
      return;
    }

    if (!this.imageFile && !this.jsonFile) {
      this.error = 'Seleziona almeno un file da salvare (planimetria e/o JSON).';
      this.message = '';
      this.refreshView();
      return;
    }

    if (this.imageFile && this.isDxfFile(this.imageFile) && !this.imagePreviewFile) {
      this.error = "Carica prima la planimetria per generare automaticamente l'anteprima SVG del DXF.";
      this.message = '';
      this.refreshView();
      return;
    }

    this.clearMessages();
    this.saveSubscription?.unsubscribe();

    if (this.imageFile) {
      this.saveSubscription = this.api.uploadPlanimetriaImage(this.selectedPianoId, this.imageFile, this.imagePreviewFile).subscribe({
        next: () => {
          this.pianiConPlanimetria.set(this.selectedPianoId!, true);
          this.imageFile = null;
          this.imagePreviewFile = null;

          if (this.jsonFile) {
            this.saveLayoutAfterImageUpload(this.selectedPianoId!);
            return;
          }

          this.message = 'Planimetria salvata correttamente.';
          this.refreshView();
          this.loadPlan(false);
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Salvataggio planimetria non riuscito.');
          this.refreshView();
        },
      });
      return;
    }

    this.saveLayoutOnly();
  }

  deletePlan(): void {
    if (!this.selectedPianoId || !confirm('Eliminare la planimetria del piano selezionato?')) return;
    this.clearMessages();
    this.deleteSubscription?.unsubscribe();
    this.deleteSubscription = this.api.deletePlanimetria(this.selectedPianoId).subscribe({
      next: () => {
        this.message = 'Planimetria eliminata.';
        this.pianiConPlanimetria.set(this.selectedPianoId!, false);
        this.clearPlan();
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione planimetria non riuscita.');
        this.refreshView();
      },
    });
  }

  loadPlan(clearExistingMessages = true): void {
    if (!this.selectedPianoId) return;
    if (clearExistingMessages) {
      this.clearMessages();
    }

    const pianoId = this.selectedPianoId;
    const requestId = ++this.currentPlanRequestId;
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();

    this.planimetriaSubscription = this.api.getPlanimetria(pianoId).subscribe({
      next: (planimetria) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        if (!planimetria) {
          this.planimetria = null;
          this.layout = null;
          this.revokeImageUrl();
          this.refreshView();
          return;
        }
        this.planimetria = planimetria;
        if (this.isPreviewablePlan(planimetria)) {
          this.loadImage(pianoId, requestId);
        } else {
          this.revokeImageUrl();
        }
        this.loadLayout(pianoId, requestId);
        this.refreshView();
      },
      error: (err) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.planimetria = null;
        this.layout = null;
        this.revokeImageUrl();
        this.error = apiErrorStatus(err) === 404
          ? 'Il piano selezionato non esiste piu o non e disponibile.'
          : apiErrorMessage(err, 'Impossibile caricare la planimetria del piano selezionato.');
        this.message = '';
        this.refreshView();
      },
    });
  }

  getPianoLabel(numero: number): string {
    if (numero === 0) return 'Piano terra';
    if (numero === 1) return 'Primo piano';
    if (numero === 2) return 'Secondo piano';
    return `Piano ${numero}`;
  }

  getPianoDisplayName(piano: Piano): string {
    return piano.nome?.trim() || this.getPianoLabel(piano.numero);
  }

  canUseEditor(): boolean {
    return !!this.selectedSedeId && !!this.selectedEdificioId;
  }

  canImportJson(): boolean {
    return this.canUseEditor() && !!this.selectedPianoId && this.hasImageLoaded();
  }

  canSavePlan(): boolean {
    return !!this.selectedPianoId && (!!this.imageFile || !!this.jsonFile);
  }

  canPreviewPlan(): boolean {
    return this.hasImageLoaded() && !!this.layout && !!this.imageSrc;
  }

  selectRoom(room: DisplayRoom): void {
    this.selectedRoomId = room.id;
    this.resetPlanPreviewScroll();
    this.refreshView();
  }

  resetZoom(): void {
    this.selectedRoomId = null;
    this.resetPlanPreviewScroll();
    this.refreshView();
  }

  onPlanImageLoad(): void {
    this.refreshView();
  }

  roomsForDisplay(): DisplayRoom[] {
    const byId = new Map<string, DisplayRoom>();
    for (const room of this.layout?.rooms ?? []) {
      byId.set(room.id, room);
    }
    for (const meeting of this.layout?.meetings ?? []) {
      byId.set(meeting.id, meeting);
    }
    return Array.from(byId.values());
  }

  visibleRooms(): PositionedRoom[] {
    return this.roomsForDisplay().filter((room): room is PositionedRoom => !!room.position);
  }

  stationsForSelectedRoom(): LayoutStation[] {
    if (!this.selectedRoomId) {
      return [];
    }
    return (this.layout?.stations ?? []).filter((station) => station.roomId === this.selectedRoomId);
  }

  visibleStations(): PositionedStation[] {
    return this.stationsForSelectedRoom().filter((station): station is PositionedStation => !!station.position);
  }

  selectedRoomStyle(): Record<string, string> {
    const zoomInput = this.selectedRoomZoomInput();
    if (!zoomInput) {
      return {};
    }

    return roomZoomStyle(zoomInput);
  }

  stationOverlayStyle(station: PositionedStation): Record<string, string> {
    const zoomInput = this.selectedRoomZoomInput();
    if (!zoomInput) {
      return {
        left: `${station.position.xPct}%`,
        top: `${station.position.yPct}%`,
      };
    }

    const zoom = roomZoom(zoomInput);
    if (!zoom || zoomInput.stageWidth <= 0 || zoomInput.stageHeight <= 0) {
      return {
        left: `${station.position.xPct}%`,
        top: `${station.position.yPct}%`,
      };
    }

    const stage = this.planStage?.nativeElement;
    const stageLeft = stage?.offsetLeft ?? 0;
    const stageTop = stage?.offsetTop ?? 0;
    const left = stageLeft + station.position.xPct / 100 * zoomInput.stageWidth * zoom.scale + zoom.translateX;
    const top = stageTop + station.position.yPct / 100 * zoomInput.stageHeight * zoom.scale + zoom.translateY;

    return {
      left: `${left}px`,
      top: `${top}px`,
    };
  }

  private selectedRoomZoomInput(): RoomZoomInput | null {
    const room = this.roomsForDisplay().find((item) => item.id === this.selectedRoomId);
    if (!room?.position) {
      return null;
    }

    return {
      roomPosition: room.position,
      stationPositions: this.stationsForRoom(room.id)
        .filter((station): station is PositionedStation => !!station.position)
        .map((station) => station.position),
      viewportWidth: this.planPreview?.nativeElement.clientWidth ?? 0,
      viewportHeight: this.planPreview?.nativeElement.clientHeight ?? 0,
      stageWidth: this.planStage?.nativeElement.offsetWidth ?? 0,
      stageHeight: this.planStage?.nativeElement.offsetHeight ?? 0,
    };
  }

  currentStep(): 1 | 2 | 3 {
    if (!this.canUseEditor()) {
      return 1;
    }
    if (!this.hasImageLoaded()) {
      return 2;
    }
    if (!this.layout) {
      return 3;
    }
    return 3;
  }

  getPianoOptionLabel(piano: Piano): string {
    const state = this.pianiConPlanimetria.get(piano.id) ? 'planimetria caricata' : 'disponibile';
    return `${this.getPianoDisplayName(piano)} - ${state}`;
  }

  private loadImage(pianoId: number, requestId: number): void {
    this.imageSubscription?.unsubscribe();
    this.imageSubscription = this.api.getPlanimetriaImage(pianoId).subscribe({
      next: (blob) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.revokeImageUrl();
        this.imageSrc = URL.createObjectURL(blob);
        this.refreshView();
      },
      error: () => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.revokeImageUrl();
        this.refreshView();
      },
    });
  }

  private loadLayout(pianoId: number, requestId: number): void {
    this.layoutSubscription?.unsubscribe();
    this.layoutSubscription = this.api.getPlanimetriaLayout(pianoId).subscribe({
      next: (layout) => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.layout = layout;
        this.selectedRoomId = null;
        this.refreshView();
      },
      error: () => {
        if (requestId !== this.currentPlanRequestId || this.selectedPianoId !== pianoId) {
          return;
        }
        this.layout = null;
        this.refreshView();
      },
    });
  }

  private clearMessages(): void {
    this.message = '';
    this.error = '';
  }

  private clearPlan(): void {
    this.currentPlanRequestId += 1;
    this.planimetriaSubscription?.unsubscribe();
    this.imageSubscription?.unsubscribe();
    this.layoutSubscription?.unsubscribe();
    this.planimetria = null;
    this.layout = null;
    this.selectedRoomId = null;
    this.imageFile = null;
    this.imagePreviewFile = null;
    this.jsonFile = null;
    this.revokeImageUrl();
  }

  private stationsForRoom(roomId: string): LayoutStation[] {
    return (this.layout?.stations ?? []).filter((station) => station.roomId === roomId);
  }

  private resetPlanPreviewScroll(): void {
    const preview = this.planPreview?.nativeElement;
    if (!preview) {
      return;
    }
    preview.scrollLeft = 0;
    preview.scrollTop = 0;
  }

  private hasImageLoaded(): boolean {
    return !!this.planimetria || !!this.imageFile || !!this.imageSrc;
  }

  private normalizeLayout(layout: PlanimetriaLayout | null): PlanimetriaLayout | null {
    if (!layout) {
      return null;
    }

    const rooms = layout.rooms ?? [];
    const stations = layout.stations ?? [];
    if (stations.length > 0) {
      return layout;
    }

    const flattenedStations = rooms.flatMap((room) =>
      ((room as { stations?: LayoutStation[] }).stations ?? []).map((station) => ({
        ...station,
        roomId: station.roomId || room.id,
        roomLabel: station.roomLabel || room.label,
      })),
    );

    return {
      ...layout,
      stations: flattenedStations,
      rooms: rooms.map((room) => {
        const nestedStations = ((room as { stations?: LayoutStation[] }).stations ?? []);
        if ((room.stationIds?.length ?? 0) > 0 || nestedStations.length === 0) {
          return room;
        }
        return {
          ...room,
          stationIds: nestedStations.map((station) => station.id),
        };
      }),
    };
  }

  private stripExtension(filename: string): string {
    const dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.slice(0, dotIndex) : filename;
  }

  private getExtension(filename: string): string {
    const dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.slice(dotIndex + 1) : 'svg';
  }

  private loadDxfPreview(file: File): void {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const conversion = this.dxfConverter.convert(String(reader.result ?? ''));
        const previewName = `${this.stripExtension(file.name)}.svg`;
        this.imagePreviewFile = new File([conversion.svgText], previewName, { type: 'image/svg+xml' });
        this.imageSrc = conversion.dataUrl;
        this.message = 'DXF convertito in anteprima SVG. Premi "Salva planimetria" per salvare originale e anteprima.';
        this.refreshView();
      } catch (err) {
        this.imagePreviewFile = null;
        this.error = err instanceof Error ? err.message : 'Conversione DXF non riuscita.';
        this.refreshView();
      }
    };
    reader.onerror = () => {
      this.error = 'Lettura del file DXF non riuscita.';
      this.refreshView();
    };
    reader.readAsText(file);
  }

  private isDxfFile(file: File): boolean {
    return this.getExtension(file.name).toLowerCase() === 'dxf';
  }

  private isDwgFile(file: File): boolean {
    return this.getExtension(file.name).toLowerCase() === 'dwg';
  }

  private isPreviewablePlan(planimetria: PlanimetriaResponse): boolean {
    const imageName = planimetria.imageName?.toLowerCase() ?? '';
    return (
      planimetria.formatoOriginale !== 'DXF'
      && planimetria.formatoOriginale !== 'DWG'
    ) || /\.(svg|png|jpg|jpeg)$/.test(imageName);
  }

  private syncPlanStatus(piani: Piano[]): void {
    const selectedEdificioId = this.selectedEdificioId;
    this.planStatusSubscription?.unsubscribe();

    if (!piani.length) {
      this.pianiConPlanimetria = new Map();
      this.refreshView();
      return;
    }

    this.planStatusSubscription = forkJoin(
      piani.map((piano) =>
        this.api.getPlanimetria(piano.id).pipe(
          map((planimetria) => ({ pianoId: piano.id, hasPlan: !!planimetria })),
          catchError(() => of({ pianoId: piano.id, hasPlan: false })),
        ),
      ),
    ).subscribe({
      next: (statuses) => {
        if (this.selectedEdificioId !== selectedEdificioId) {
          return;
        }

        this.pianiConPlanimetria = new Map(statuses.map((status) => [status.pianoId, status.hasPlan]));
        if (this.selectedPianoId && !this.piani.some((piano) => piano.id === this.selectedPianoId)) {
          this.selectedPianoId = null;
        }
        this.refreshView();
      },
      error: () => this.refreshView(),
    });
  }

  private saveLayoutAfterImageUpload(pianoId: number): void {
    if (!this.jsonFile) {
      this.message = 'Planimetria salvata correttamente.';
      this.refreshView();
      this.loadPlan(false);
      return;
    }

    this.importSubscription?.unsubscribe();
    this.importSubscription = this.api.importPlanimetriaJson(pianoId, this.jsonFile).subscribe({
      next: () => {
        this.imageFile = null;
        this.jsonFile = null;
        this.message = 'Planimetria e layout salvati correttamente.';
        this.refreshView();
        this.loadPlan(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Salvataggio layout non riuscito.');
        this.refreshView();
      },
    });
  }

  private saveLayoutOnly(): void {
    if (!this.selectedPianoId || !this.jsonFile) {
      this.error = 'Seleziona un file JSON da salvare.';
      this.refreshView();
      return;
    }

    if (!this.planimetria) {
      this.error = "Carica prima l'immagine della planimetria per il piano selezionato.";
      this.refreshView();
      return;
    }

    this.importSubscription?.unsubscribe();
    this.importSubscription = this.api.importPlanimetriaJson(this.selectedPianoId, this.jsonFile).subscribe({
      next: () => {
        this.jsonFile = null;
        this.message = 'Planimetria salvata correttamente.';
        this.refreshView();
        this.loadPlan(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Salvataggio layout non riuscito.');
        this.refreshView();
      },
    });
  }

  private revokeImageUrl(): void {
    if (this.imageSrc) {
      URL.revokeObjectURL(this.imageSrc);
      this.imageSrc = '';
    }
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }
}
