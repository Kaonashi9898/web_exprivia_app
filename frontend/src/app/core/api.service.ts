import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import {
  CreatePrenotazioneRequest,
  DashboardPrenotazione,
  Edificio,
  Gruppo,
  GruppoPostazione,
  Piano,
  PlanimetriaLayout,
  PlanimetriaResponse,
  Postazione,
  PostazioneRequest,
  Prenotazione,
  RegisterRequest,
  RuoloUtente,
  Sede,
  Stanza,
  UpdatePrenotazioneRequest,
  Utente,
} from './app.models';
import { environment } from '../../environments/environment';

const UTENTI_API = environment.utentiApiBaseUrl;
const LOCATION_API = environment.locationApiBaseUrl;
const PRENOTAZIONI_API = environment.prenotazioniApiBaseUrl;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  listUsers() {
    return this.http.get<Utente[]>(`${UTENTI_API}/api/utenti`);
  }

  createUser(request: RegisterRequest) {
    return this.http.post<Utente>(`${UTENTI_API}/api/utenti`, request);
  }

  updateUserRole(id: number, ruolo: RuoloUtente) {
    return this.http.patch<Utente>(`${UTENTI_API}/api/utenti/${id}/ruolo`, { ruolo });
  }

  deleteUser(id: number) {
    return this.http.delete<void>(`${UTENTI_API}/api/utenti/${id}`);
  }

  listGroups() {
    return this.http.get<Gruppo[]>(`${UTENTI_API}/api/gruppi`);
  }

  listMyGroups() {
    return this.http.get<Gruppo[]>(`${UTENTI_API}/api/gruppi/me`);
  }

  createGroup(nome: string) {
    const params = new HttpParams().set('nome', nome);
    return this.http.post<Gruppo>(`${UTENTI_API}/api/gruppi`, null, { params });
  }

  updateGroup(id: number, nome: string) {
    return this.http.put<Gruppo>(`${UTENTI_API}/api/gruppi/${id}`, { nome });
  }

  deleteGroup(id: number) {
    return this.http.delete<void>(`${UTENTI_API}/api/gruppi/${id}`);
  }

  listUsersByGroup(groupId: number) {
    return this.http.get<Utente[]>(`${UTENTI_API}/api/gruppi/${groupId}/utenti`);
  }

  addUserToGroup(groupId: number, userId: number) {
    return this.http.post<void>(`${UTENTI_API}/api/gruppi/${groupId}/utenti/${userId}`, null);
  }

  removeUserFromGroup(groupId: number, userId: number) {
    return this.http.delete<void>(`${UTENTI_API}/api/gruppi/${groupId}/utenti/${userId}`);
  }

  listSeatGroups(postazioneId: number) {
    return this.http.get<GruppoPostazione[]>(`${LOCATION_API}/api/gruppi-postazioni/postazione/${postazioneId}`);
  }

  addGroupToSeat(groupId: number, postazioneId: number) {
    return this.http.post<GruppoPostazione>(`${LOCATION_API}/api/gruppi-postazioni/gruppo/${groupId}/postazione/${postazioneId}`, null);
  }

  removeGroupFromSeat(groupId: number, postazioneId: number) {
    return this.http.delete<void>(`${LOCATION_API}/api/gruppi-postazioni/gruppo/${groupId}/postazione/${postazioneId}`);
  }

  listSedi() {
    return this.http.get<Sede[]>(`${LOCATION_API}/api/sedi`);
  }

  createSede(request: Omit<Sede, 'id'>) {
    return this.http.post<Sede>(`${LOCATION_API}/api/sedi`, request);
  }

  deleteSede(id: number) {
    return this.http.delete<void>(`${LOCATION_API}/api/sedi/${id}`);
  }

  listEdifici(sedeId: number) {
    return this.http.get<Edificio[]>(`${LOCATION_API}/api/edifici/sede/${sedeId}`);
  }

  createEdificio(request: { nome: string; sedeId: number }) {
    return this.http.post<Edificio>(`${LOCATION_API}/api/edifici`, request);
  }

  deleteEdificio(id: number) {
    return this.http.delete<void>(`${LOCATION_API}/api/edifici/${id}`);
  }

  listPiani(edificioId: number) {
    return this.http.get<Piano[]>(`${LOCATION_API}/api/piani/edificio/${edificioId}`);
  }

  createPiano(request: { numero: number; nome?: string | null; edificioId: number }) {
    return this.http.post<Piano>(`${LOCATION_API}/api/piani`, request);
  }

  deletePiano(id: number) {
    return this.http.delete<void>(`${LOCATION_API}/api/piani/${id}`);
  }

  listStanze(pianoId: number) {
    return this.http.get<Stanza[]>(`${LOCATION_API}/api/stanze/piano/${pianoId}`);
  }

  createStanza(request: { nome: string; tipo: 'ROOM' | 'MEETING_ROOM'; layoutElementId?: string | null; xPct?: number | null; yPct?: number | null; pianoId: number }) {
    return this.http.post<Stanza>(`${LOCATION_API}/api/stanze`, request);
  }

  listPostazioni(stanzaId: number) {
    return this.http.get<Postazione[]>(`${LOCATION_API}/api/postazioni/stanza/${stanzaId}`);
  }

  createPostazione(request: PostazioneRequest) {
    return this.http.post<Postazione>(`${LOCATION_API}/api/postazioni`, request);
  }

  updatePostazione(id: number, request: PostazioneRequest) {
    return this.http.put<Postazione>(`${LOCATION_API}/api/postazioni/${id}`, request);
  }

  updatePostazioneStato(id: number, stato: string) {
    const params = new HttpParams().set('stato', stato);
    return this.http.patch<Postazione>(`${LOCATION_API}/api/postazioni/${id}/stato`, null, { params });
  }

  deletePostazione(id: number) {
    return this.http.delete<void>(`${LOCATION_API}/api/postazioni/${id}`);
  }

  listMyBookings(data?: string) {
    const params = data ? new HttpParams().set('data', data) : undefined;
    return this.http.get<Prenotazione[]>(`${PRENOTAZIONI_API}/api/prenotazioni/mie`, { params }).pipe(
      map((bookings) => bookings.map((booking) => this.normalizeBookingTimes(booking))),
    );
  }

  listMyDashboardBookings(data?: string) {
    const params = data ? new HttpParams().set('data', data) : undefined;
    return this.http.get<DashboardPrenotazione[]>(`${PRENOTAZIONI_API}/api/prenotazioni/mie/dashboard`, { params }).pipe(
      map((bookings) => bookings.map((booking) => this.normalizeBookingTimes(booking))),
    );
  }

  listBookings(data?: string, postazioneId?: number) {
    let params = new HttpParams();
    if (data) {
      params = params.set('data', data);
    }
    if (postazioneId) {
      params = params.set('postazioneId', postazioneId);
    }
    return this.http.get<Prenotazione[]>(`${PRENOTAZIONI_API}/api/prenotazioni`, { params }).pipe(
      map((bookings) => bookings.map((booking) => this.normalizeBookingTimes(booking))),
    );
  }

  listBookingsByMeetingRoom(stanzaId: number, data?: string) {
    const params = data ? new HttpParams().set('data', data) : undefined;
    return this.http.get<Prenotazione[]>(`${PRENOTAZIONI_API}/api/prenotazioni/meeting-room/${stanzaId}`, { params }).pipe(
      map((bookings) => bookings.map((booking) => this.normalizeBookingTimes(booking))),
    );
  }

  listBookingsByPostazione(postazioneId: number, data?: string) {
    const params = data ? new HttpParams().set('data', data) : undefined;
    return this.http.get<Prenotazione[]>(`${PRENOTAZIONI_API}/api/prenotazioni/postazione/${postazioneId}`, { params }).pipe(
      map((bookings) => bookings.map((booking) => this.normalizeBookingTimes(booking))),
    );
  }

  createBooking(request: CreatePrenotazioneRequest) {
    return this.http.post<Prenotazione>(`${PRENOTAZIONI_API}/api/prenotazioni`, request).pipe(
      map((booking) => this.normalizeBookingTimes(booking)),
    );
  }

  createMeetingRoomBooking(request: CreatePrenotazioneRequest) {
    return this.http.post<Prenotazione>(`${PRENOTAZIONI_API}/api/prenotazioni/meeting-room`, request).pipe(
      map((booking) => this.normalizeBookingTimes(booking)),
    );
  }

  updateBooking(id: number, request: UpdatePrenotazioneRequest) {
    return this.http.put<Prenotazione>(`${PRENOTAZIONI_API}/api/prenotazioni/${id}`, request).pipe(
      map((booking) => this.normalizeBookingTimes(booking)),
    );
  }

  cancelBooking(id: number) {
    return this.http.delete<void>(`${PRENOTAZIONI_API}/api/prenotazioni/${id}`);
  }

  getPlanimetria(pianoId: number) {
    return this.http.get<PlanimetriaResponse | null>(`${LOCATION_API}/api/piani/${pianoId}/planimetria`);
  }

  getPlanimetriaLayout(pianoId: number) {
    return this.http.get<PlanimetriaLayout>(`${LOCATION_API}/api/piani/${pianoId}/planimetria/layout`);
  }

  getPlanimetriaImage(pianoId: number) {
    return this.http.get(`${LOCATION_API}/api/piani/${pianoId}/planimetria/image`, {
      responseType: 'blob',
    });
  }

  uploadPlanimetriaImage(pianoId: number, file: File, previewSvgFile?: File | null) {
    const data = new FormData();
    data.append('file', file);
    if (previewSvgFile) {
      data.append('previewSvg', previewSvgFile);
    }
    return this.http.post<PlanimetriaResponse>(`${LOCATION_API}/api/piani/${pianoId}/planimetria/image`, data);
  }

  importPlanimetriaJson(pianoId: number, file: File) {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<PlanimetriaResponse>(`${LOCATION_API}/api/piani/${pianoId}/planimetria/json`, data);
  }

  deletePlanimetria(pianoId: number) {
    return this.http.delete<void>(`${LOCATION_API}/api/piani/${pianoId}/planimetria`);
  }

  private normalizeBookingTimes<T extends Prenotazione | DashboardPrenotazione>(booking: T): T {
    return {
      ...booking,
      oraInizio: this.withoutSeconds(booking.oraInizio),
      oraFine: this.withoutSeconds(booking.oraFine),
    };
  }

  private withoutSeconds(value: string | null | undefined): string {
    if (!value) {
      return '';
    }

    return value.slice(0, 5);
  }
}
