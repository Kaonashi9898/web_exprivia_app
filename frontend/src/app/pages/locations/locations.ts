import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Edificio, Piano, PlanimetriaLayout, PlanimetriaResponse, Postazione, Prenotazione, Sede } from '../../core/app.models';

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
  selector: 'app-locations',
  imports: [FormsModule, NgStyle],
  templateUrl: './locations.html',
  styleUrl: './locations.css',
})
export class LocationsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  sedi: Sede[] = EXPRIVIA_ITALIA_SEDI;
  edifici: Edificio[] = [];
  piani: Piano[] = [];
  postazioni: Postazione[] = [];
  bookings: Prenotazione[] = [];
  selectedSedeId: number | null = null;
  selectedEdificioId: number | null = null;
  selectedPianoId: number | null = null;
  selectedRoomId: string | null = null;
  selectedStation: LayoutStation | null = null;
  selectedPostazione: Postazione | null = null;

  planimetria: PlanimetriaResponse | null = null;
  layout: PlanimetriaLayout | null = null;
  imageSrc = '';
  unavailableMessage = '';
  message = '';
  error = '';
  sediLoading = false;

  bookingDate = new Date().toISOString().slice(0, 10);
  startTime = '09:00';
  endTime = '18:00';

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
    this.resetPlan();
    this.edifici = [];
    this.piani = [];
    this.selectedEdificioId = null;
    this.selectedPianoId = null;

    if (!this.selectedSedeId) {
      return;
    }

    this.api.listEdifici(this.selectedSedeId).subscribe({
        next: (edifici) => {
          this.edifici = edifici;
          if (!edifici.length) {
            this.unavailableMessage = 'Per questa sede non sono ancora configurati edifici.';
          }
          this.refreshView();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Impossibile caricare la sede.';
          this.refreshView();
        },
      });
  }

  onEdificioChange(): void {
    this.resetPlan();
    this.piani = [];
    this.selectedPianoId = null;

    if (!this.selectedEdificioId) {
      return;
    }

    this.api.listPiani(this.selectedEdificioId).subscribe({
      next: (piani) => {
        this.piani = piani.filter((piano) => this.hasPianoName(piano));
        if (!this.piani.length) {
          this.unavailableMessage = 'Per questo edificio non sono ancora stati creati piani.';
        }
        this.refreshView();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Impossibile caricare i piani.';
        this.refreshView();
      },
    });
  }

  onPianoChange(): void {
    this.resetPlan();
    if (this.selectedPianoId) {
      this.loadPlan(this.selectedPianoId);
    }
  }

  selectRoom(room: DisplayRoom): void {
    this.selectedRoomId = room.id;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.refreshView();
  }

  resetZoom(): void {
    this.selectedRoomId = null;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.refreshView();
  }

  selectStation(station: LayoutStation): void {
    if (!this.stationIsAvailable(station)) {
      return;
    }

    this.selectedStation = station;
    this.selectedRoomId = station.roomId ?? this.selectedRoomId;
    this.selectedPostazione = this.findPostazione(station);
    this.refreshView();
  }

  createBooking(): void {
    if (!this.selectedPostazione) {
      return;
    }

    this.clearMessages();
    this.api
      .createBooking({
        postazioneId: this.selectedPostazione.id,
        dataPrenotazione: this.bookingDate,
        oraInizio: this.startTime,
        oraFine: this.endTime,
      })
      .subscribe({
        next: () => {
          this.message = 'Prenotazione confermata.';
          this.loadBookingsForCurrentPlan();
          this.refreshView();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Prenotazione non riuscita.';
          this.refreshView();
        },
      });
  }

  stationsForSelectedRoom(): LayoutStation[] {
    const stations = this.layout?.stations ?? [];
    if (!this.selectedRoomId) {
      return [];
    }
    return stations.filter((station) => station.roomId === this.selectedRoomId);
  }

  selectedRoomLabel(): string {
    return this.roomsForDisplay().find((room) => room.id === this.selectedRoomId)?.label ?? 'Seleziona una stanza';
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

  visibleStations(): PositionedStation[] {
    return this.stationsForSelectedRoom().filter((station): station is PositionedStation => !!station.position);
  }

  stationIsBooked(station: LayoutStation): boolean {
    return !!this.bookingForStation(station);
  }

  stationIsAvailable(station: LayoutStation): boolean {
    const postazione = this.findPostazione(station);
    return !!postazione && postazione.stato === 'DISPONIBILE' && !this.stationIsBooked(station);
  }

  bookingForStation(station: LayoutStation): Prenotazione | null {
    const postazione = this.findPostazione(station);
    if (!postazione) {
      return null;
    }

    return this.bookings.find((booking) =>
      booking.stato === 'CONFERMATA' && booking.postazioneId === postazione.id,
    ) ?? null;
  }

  stationTooltip(station: LayoutStation): string {
    const booking = this.bookingForStation(station);
    if (booking) {
      return `Prenotata da ${booking.utenteFullName}, dalle ${booking.oraInizio} alle ${booking.oraFine}`;
    }

    const postazione = this.findPostazione(station);
    if (!postazione) {
      return 'Postazione non sincronizzata';
    }
    if (postazione.stato !== 'DISPONIBILE') {
      return 'Postazione non prenotabile';
    }
    return station.label;
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

  private loadPlan(pianoId: number): void {
    this.unavailableMessage = '';
    this.api.getPlanimetria(pianoId).subscribe({
        next: (planimetria) => {
          if (!planimetria) {
            this.unavailableMessage = this.noPlanMessage();
            this.refreshView();
            return;
          }
        this.planimetria = planimetria;
        if (planimetria.formatoOriginale === 'DXF' || planimetria.formatoOriginale === 'DWG') {
          this.revokeImageUrl();
          this.unavailableMessage =
            'Planimetria tecnica caricata. Per visualizzarla qui serve anche una versione PNG, JPG o SVG.';
        } else {
          this.loadImage(pianoId);
        }
        this.loadLayoutAndSeats(pianoId);
        this.refreshView();
      },
      error: () => {
        this.unavailableMessage = this.noPlanMessage();
        this.refreshView();
      },
    });
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

  private loadLayoutAndSeats(pianoId: number): void {
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

    this.api
      .listStanze(pianoId)
      .pipe(
        switchMap((stanze) =>
          stanze.length ? forkJoin(stanze.map((stanza) => this.api.listPostazioni(stanza.id))) : of([]),
        ),
      )
      .subscribe({
        next: (groups) => {
          this.postazioni = groups.flat();
          this.loadBookingsForCurrentPlan();
          this.refreshView();
        },
        error: () => {
          this.postazioni = [];
          this.refreshView();
        },
      });
  }

  findPostazione(station: LayoutStation): Postazione | null {
    return (
      this.postazioni.find((postazione) => postazione.cadId && postazione.cadId === station.id) ??
      this.postazioni.find((postazione) => postazione.codice === station.label) ??
      null
    );
  }

  private resetPlan(): void {
    this.clearMessages();
    this.planimetria = null;
    this.layout = null;
    this.postazioni = [];
    this.bookings = [];
    this.selectedRoomId = null;
    this.selectedStation = null;
    this.selectedPostazione = null;
    this.unavailableMessage = '';
    this.revokeImageUrl();
  }

  private clearMessages(): void {
    this.message = '';
    this.error = '';
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

  loadBookingsForCurrentPlan(): void {
    if (!this.selectedPianoId || !this.postazioni.length) {
      this.bookings = [];
      this.refreshView();
      return;
    }

    this.api.listBookings(this.bookingDate || undefined).subscribe({
      next: (bookings) => {
        const postazioneIds = new Set(this.postazioni.map((postazione) => postazione.id));
        this.bookings = bookings.filter((booking) => postazioneIds.has(booking.postazioneId));
        this.refreshView();
      },
      error: () => {
        this.bookings = [];
        this.refreshView();
      },
    });
  }

  private noPlanMessage(): string {
    const sede = this.sedi.find((item) => item.id === this.selectedSedeId);
    const piano = this.piani.find((item) => item.id === this.selectedPianoId);
    const sedeLabel = sede ? `${sede.nome} - ${sede.citta}` : 'questa sede';
    const pianoLabel = piano ? this.getPianoDisplayName(piano) : 'questo piano';
    return `Per ${sedeLabel} e per ${pianoLabel} non c'e ancora la planimetria.`;
  }
}
