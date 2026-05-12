export type RuoloUtente = 'ADMIN' | 'BUILDING_MANAGER' | 'RECEPTION' | 'USER' | 'GUEST';

export interface Utente {
  id: number;
  fullName: string;
  email: string;
  ruolo: RuoloUtente;
}

export interface LoginResponse {
  user: Utente;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  ruolo: RuoloUtente;
}

export interface Gruppo {
  id: number;
  nome: string;
}

export interface GruppoPostazione {
  id: number;
  gruppoId: number;
  postazioneId: number;
  postazioneCodice: string;
}

export interface Sede {
  id: number;
  nome: string;
  indirizzo: string;
  citta: string;
  latitudine?: number | null;
  longitudine?: number | null;
}

export interface Edificio {
  id: number;
  nome: string;
  sedeId: number;
  sedeNome: string;
}

export interface Piano {
  id: number;
  numero: number;
  nome?: string | null;
  edificioId: number;
  edificioNome: string;
}

export interface Stanza {
  id: number;
  nome: string;
  tipo: TipoStanza;
  layoutElementId?: string | null;
  xPct?: number | null;
  yPct?: number | null;
  pianoId: number;
  pianoNumero: number;
}

export type TipoStanza = 'ROOM' | 'MEETING_ROOM';
export type StatoPostazione = 'DISPONIBILE' | 'NON_DISPONIBILE' | 'MANUTENZIONE' | 'CAMBIO_DESTINAZIONE';

export interface Postazione {
  id: number;
  codice: string;
  layoutElementId?: string | null;
  stato: StatoPostazione;
  xPct?: number | null;
  yPct?: number | null;
  stanzaId: number;
  stanzaNome: string;
}

export interface PostazioneRequest {
  codice: string;
  layoutElementId?: string | null;
  stato: StatoPostazione;
  xPct?: number | null;
  yPct?: number | null;
  stanzaId: number;
}

export type StatoPrenotazione = 'CONFERMATA' | 'ANNULLATA';
export type TipoRisorsaPrenotata = 'POSTAZIONE' | 'MEETING_ROOM';

export interface Prenotazione {
  id: number;
  utenteId?: number | null;
  utenteEmail?: string | null;
  utenteFullName?: string | null;
  tipoRisorsaPrenotata: TipoRisorsaPrenotata;
  risorsaLabel: string;
  postazioneId?: number | null;
  postazioneCodice?: string | null;
  meetingRoomStanzaId?: number | null;
  meetingRoomNome?: string | null;
  stanzaId: number;
  stanzaNome: string;
  dataPrenotazione: string;
  oraInizio: string;
  oraFine: string;
  stato: StatoPrenotazione;
  createdAt: string;
  updatedAt: string;
}

export interface DashboardPrenotazione extends Prenotazione {
  sedeLabel: string;
  pianoLabel: string;
  pianoId?: number | null;
}

export interface CreatePrenotazioneRequest {
  postazioneId?: number | null;
  meetingRoomStanzaId?: number | null;
  dataPrenotazione: string;
  oraInizio: string;
  oraFine: string;
}

export interface UpdatePrenotazioneRequest {
  dataPrenotazione: string;
  oraInizio: string;
  oraFine: string;
}

export interface PlanimetriaResponse {
  id: number;
  pianoId: number;
  imageName?: string | null;
  formatoOriginale?: string | null;
  imageUrl: string;
  postazioniUrl: string;
  layoutUrl: string;
}

export interface PlanimetriaLayout {
  exportedAt?: string;
  image?: {
    filename?: string;
    naturalWidth?: number;
    naturalHeight?: number;
  };
  rooms?: Array<{
    id: string;
    label: string;
    position?: { xPct: number; yPct: number };
    stationIds?: string[];
  }>;
  meetings?: Array<{
    id: string;
    label: string;
    position?: { xPct: number; yPct: number };
    stationIds?: string[];
  }>;
  stations?: Array<{
    id: string;
    label: string;
    position?: { xPct: number; yPct: number };
    roomId?: string;
    roomLabel?: string;
  }>;
}
