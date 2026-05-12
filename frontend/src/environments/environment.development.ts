import { RuntimeConfig } from './runtime-config';

const runtimeConfig = (globalThis as { __EXPRIVIA_CONFIG__?: Partial<RuntimeConfig> }).__EXPRIVIA_CONFIG__;

const currentHostname = globalThis.location?.hostname ?? '';
const isLocalhost = currentHostname === 'localhost' || currentHostname === '127.0.0.1' || currentHostname === '::1';

/*
 * v05
 * - Accesso locale diretto al frontend Docker:
 *     http://localhost:13010
 *   mantiene le chiamate esplicite verso le porte host 13030/13040/13050.
 *
 * - Accesso tramite Apache / dominio pubblico:
 *     https://prenotazioniexpba.exptraining.it:8082
 *     https://192.168.178.31:8444
 *   usa path relativi, così Apache può instradare verso i tre microservizi.
 */
export const environment: RuntimeConfig = {
  utentiApiBaseUrl: runtimeConfig?.utentiApiBaseUrl ?? (isLocalhost ? 'http://localhost:13030' : '/api-utenti'),
  locationApiBaseUrl: runtimeConfig?.locationApiBaseUrl ?? (isLocalhost ? 'http://localhost:13040' : '/api-location'),
  prenotazioniApiBaseUrl: runtimeConfig?.prenotazioniApiBaseUrl ?? (isLocalhost ? 'http://localhost:13050' : '/api-prenotazioni'),
  floorPlanEditorUrl: runtimeConfig?.floorPlanEditorUrl ?? (isLocalhost ? 'http://localhost:13020/editor/' : '/editor/'),
};
