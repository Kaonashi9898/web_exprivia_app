import { RuntimeConfig } from './runtime-config';

const runtimeConfig = (globalThis as { __EXPRIVIA_CONFIG__?: Partial<RuntimeConfig> }).__EXPRIVIA_CONFIG__;
const currentHostname = globalThis.location?.hostname ?? '';
const isLocalhost = currentHostname === 'localhost' || currentHostname === '127.0.0.1' || currentHostname === '::1';

export const environment: RuntimeConfig = {
  utentiApiBaseUrl: runtimeConfig?.utentiApiBaseUrl ?? (isLocalhost ? 'http://localhost:13030' : '/api-utenti'),
  locationApiBaseUrl: runtimeConfig?.locationApiBaseUrl ?? (isLocalhost ? 'http://localhost:13040' : '/api-location'),
  prenotazioniApiBaseUrl: runtimeConfig?.prenotazioniApiBaseUrl ?? (isLocalhost ? 'http://localhost:13050' : '/api-prenotazioni'),
  floorPlanEditorUrl: runtimeConfig?.floorPlanEditorUrl ?? (isLocalhost ? 'http://localhost:13020/editor/' : '/editor/'),
};
