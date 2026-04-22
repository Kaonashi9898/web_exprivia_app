import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, PlanimetriaLayout, PlanimetriaResponse, Sede } from '../../core/app.models';

const EXPRIVIA_ITALIA_SEDI: Sede[] = [
  { id: 1, nome: 'Exprivia - Roma Bufalotta', indirizzo: 'Via della Bufalotta 378', citta: 'Roma' },
  { id: 2, nome: 'Exprivia - Molfetta Headquarter', indirizzo: 'Via A. Olivetti 11', citta: 'Molfetta' },
  { id: 3, nome: 'Exprivia - Molfetta Agnelli', indirizzo: 'Via Giovanni Agnelli 5', citta: 'Molfetta' },
  { id: 4, nome: 'Exprivia - Milano', indirizzo: 'Via dei Valtorta 43', citta: 'Milano' },
  { id: 5, nome: 'Exprivia - Lecce', indirizzo: 'Campus Ecotekne, Via Monteroni 165', citta: 'Lecce' },
  { id: 6, nome: 'Exprivia - Matera', indirizzo: 'Via Giovanni Agnelli snc', citta: 'Matera' },
  { id: 7, nome: 'Exprivia - Palermo', indirizzo: 'Viale Regione Siciliana Nord-Ovest 7275', citta: 'Palermo' },
  { id: 8, nome: 'Exprivia - Trento', indirizzo: 'Via Alcide De Gasperi 77', citta: 'Trento' },
  { id: 9, nome: 'Exprivia - Vicenza', indirizzo: 'Via L. Lazzaro Zamenhof 817', citta: 'Vicenza' },
];

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
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

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
  planFileName = '';
  imageFileRenamed = false;
  jsonFile: File | null = null;
  message = '';
  error = '';
  sediLoading = false;
  pianiLoading = false;
  readonly editorUrl = 'http://localhost:4202/editor';

  ngOnInit(): void {
    this.sediLoading = true;
    this.api.listSedi().subscribe({
      next: (sedi) => {
        this.sedi = sedi.length ? sedi : EXPRIVIA_ITALIA_SEDI;
        this.sediLoading = false;
        this.refreshView();
      },
      error: (err) => {
        this.sediLoading = false;
        this.error = err?.error?.message ?? 'Impossibile caricare le sedi.';
        this.refreshView();
      },
    });
  }

  ngOnDestroy(): void {
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
    this.api.listEdifici(this.selectedSedeId).subscribe({
      next: (edifici) => {
        this.edifici = edifici;
        this.pianiLoading = false;
        this.refreshView();
      },
      error: (err) => {
        this.pianiLoading = false;
        this.error = err?.error?.message ?? 'Impossibile caricare gli edifici della sede.';
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
    this.api.listPiani(this.selectedEdificioId).subscribe({
      next: (piani) => {
        this.allPiani = piani;
        this.piani = piani.filter((piano) => this.hasPianoName(piano));
        this.pianiLoading = false;
        this.syncPlanStatus(this.piani);
      },
      error: (err) => {
        this.pianiLoading = false;
        this.error = err?.error?.message ?? 'Impossibile caricare i piani della sede.';
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
    this.planFileName = this.imageFile ? this.stripExtension(this.imageFile.name) : '';
    this.imageFileRenamed = false;
    this.refreshView();
  }

  setJsonFile(event: Event): void {
    this.jsonFile = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  uploadImage(): void {
    if (!this.selectedEdificioId || !this.imageFile || !this.imageFileRenamed) return;
    this.clearMessages();
    this.ensureUploadPiano().pipe(
      switchMap((piano) => {
        this.selectedPianoId = piano.id;
        return this.api.uploadPlanimetriaImage(piano.id, this.imageFile!);
      }),
    ).subscribe({
      next: () => {
        this.message = 'Planimetria caricata.';
        const renamedPlanName = this.planFileName.trim();
        this.allPiani = this.allPiani.map((piano) =>
          piano.id === this.selectedPianoId ? { ...piano, nome: renamedPlanName || piano.nome } : piano,
        );
        this.piani = this.piani.map((piano) =>
          piano.id === this.selectedPianoId ? { ...piano, nome: renamedPlanName || piano.nome } : piano,
        );
        this.imageFile = null;
        this.pianiConPlanimetria.set(this.selectedPianoId!, true);
        this.refreshView();
        this.loadPlan(false);
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Upload planimetria non riuscito.';
        this.refreshView();
      },
    });
  }

  renameImageFile(): void {
    if (!this.imageFile || !this.planFileName.trim()) {
      return;
    }

    const extension = this.getExtension(this.imageFile.name);
    const safeName = this.planFileName.trim().replace(/[^a-zA-Z0-9-_ ]/g, '-').replace(/\s+/g, ' ');
    this.imageFile = new File([this.imageFile], `${safeName}.${extension}`, { type: this.imageFile.type });
    this.imageFileRenamed = true;
    this.message = `File rinominato in ${this.imageFile.name}.`;
    this.error = '';
    this.refreshView();
  }

  importJson(): void {
    if (!this.selectedPianoId || !this.planimetria || !this.jsonFile) return;
    this.clearMessages();
    this.api.importPlanimetriaJson(this.selectedPianoId, this.jsonFile).subscribe({
      next: () => {
        this.message = 'Layout importato e sincronizzato.';
        this.jsonFile = null;
        this.refreshView();
        this.loadPlan(false);
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Import JSON non riuscito.';
        this.refreshView();
      },
    });
  }

  deletePlan(): void {
    if (!this.selectedPianoId || !confirm('Eliminare la planimetria del piano selezionato?')) return;
    this.clearMessages();
    this.api.deletePlanimetria(this.selectedPianoId).subscribe({
      next: () => {
        this.message = 'Planimetria eliminata.';
        this.pianiConPlanimetria.set(this.selectedPianoId!, false);
        this.allPiani = this.allPiani.map((piano) =>
          piano.id === this.selectedPianoId ? { ...piano, nome: null } : piano,
        );
        this.piani = this.piani.map((piano) =>
          piano.id === this.selectedPianoId ? { ...piano, nome: null } : piano,
        ).filter((piano) => this.hasPianoName(piano));
        this.clearPlan();
        this.refreshView();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Eliminazione planimetria non riuscita.';
        this.refreshView();
      },
    });
  }

  savePlan(): void {
    if (!this.canPreviewPlan()) {
      return;
    }

    this.message = 'Planimetria salvata nel database.';
    this.error = '';
    this.refreshView();
  }

  viewPreview(): void {
    if (!this.canPreviewPlan()) {
      return;
    }

    this.message = 'Anteprima aggiornata.';
    this.error = '';
    this.loadPlan(false);
  }

  loadPlan(clearExistingMessages = true): void {
    if (!this.selectedPianoId) return;
    if (clearExistingMessages) {
      this.clearMessages();
    }

    this.api.getPlanimetria(this.selectedPianoId).subscribe({
      next: (planimetria) => {
        if (!planimetria) {
          this.planimetria = null;
          this.layout = null;
          this.revokeImageUrl();
          this.refreshView();
          return;
        }
        this.planimetria = planimetria;
        if (planimetria.formatoOriginale !== 'DXF' && planimetria.formatoOriginale !== 'DWG') {
          this.loadImage(this.selectedPianoId!);
        } else {
          this.revokeImageUrl();
        }
        this.loadLayout(this.selectedPianoId!);
        this.refreshView();
      },
      error: () => {
        this.planimetria = null;
        this.layout = null;
        this.revokeImageUrl();
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

  hasPianoName(piano: Piano): boolean {
    return !!piano.nome?.trim();
  }

  canUseEditor(): boolean {
    return !!this.selectedSedeId && !!this.selectedEdificioId;
  }

  canImportJson(): boolean {
    return this.canUseEditor() && !!this.selectedPianoId && !!this.planimetria;
  }

  canPreviewPlan(): boolean {
    return !!this.planimetria && !!this.layout;
  }

  selectRoom(room: DisplayRoom): void {
    this.selectedRoomId = room.id;
    this.refreshView();
  }

  resetZoom(): void {
    this.selectedRoomId = null;
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
    const room = this.roomsForDisplay().find((item) => item.id === this.selectedRoomId);
    if (!room?.position) {
      return {};
    }

    return {
      transform: 'scale(2.15)',
      transformOrigin: `${room.position.xPct}% ${room.position.yPct}%`,
    };
  }

  currentStep(): 1 | 2 | 3 {
    if (!this.canUseEditor()) {
      return 1;
    }
    if (!this.planimetria) {
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

  private loadImage(pianoId: number): void {
    this.api.getPlanimetriaImage(pianoId).subscribe({
      next: (blob) => {
        this.revokeImageUrl();
        this.imageSrc = URL.createObjectURL(blob);
        this.refreshView();
      },
      error: () => {
        this.revokeImageUrl();
        this.refreshView();
      },
    });
  }

  private loadLayout(pianoId: number): void {
    this.api.getPlanimetriaLayout(pianoId).subscribe({
      next: (layout) => {
        this.layout = layout;
        this.selectedRoomId = null;
        this.refreshView();
      },
      error: () => {
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
    this.planimetria = null;
    this.layout = null;
    this.selectedRoomId = null;
    this.imageFile = null;
    this.planFileName = '';
    this.imageFileRenamed = false;
    this.jsonFile = null;
    this.revokeImageUrl();
  }

  private stripExtension(filename: string): string {
    const dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.slice(0, dotIndex) : filename;
  }

  private getExtension(filename: string): string {
    const dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.slice(dotIndex + 1) : 'svg';
  }

  private syncPlanStatus(piani: Piano[]): void {
    if (!piani.length) {
      this.pianiConPlanimetria = new Map();
      this.refreshView();
      return;
    }

    forkJoin(
      piani.map((piano) =>
        this.api.getPlanimetria(piano.id).pipe(
          map((planimetria) => ({ pianoId: piano.id, hasPlan: !!planimetria })),
          catchError(() => of({ pianoId: piano.id, hasPlan: false })),
        ),
      ),
    ).subscribe({
      next: (statuses) => {
        this.pianiConPlanimetria = new Map(statuses.map((status) => [status.pianoId, status.hasPlan]));
        this.selectedPianoId = null;
        this.refreshView();
      },
      error: () => this.refreshView(),
    });
  }

  private ensureUploadPiano() {
    const selected = this.piani.find((piano) => piano.id === this.selectedPianoId);
    if (selected) {
      return of(selected);
    }

    const nextNumero = this.allPiani.length
      ? Math.max(...this.allPiani.map((piano) => piano.numero)) + 1
      : 0;

    return this.api.createPiano({
      numero: nextNumero,
      nome: this.planFileName.trim(),
      edificioId: this.selectedEdificioId!,
    }).pipe(
      map((piano) => {
        this.allPiani = [...this.allPiani, piano];
        this.piani = [...this.piani, piano];
        return piano;
      }),
    );
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
