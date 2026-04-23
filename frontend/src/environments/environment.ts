import { RuntimeConfig } from './runtime-config';

const runtimeConfig = (globalThis as { __EXPRIVIA_CONFIG__?: Partial<RuntimeConfig> }).__EXPRIVIA_CONFIG__;

export const environment: RuntimeConfig = {
  utentiApiBaseUrl: runtimeConfig?.utentiApiBaseUrl ?? '/api-utenti',
  locationApiBaseUrl: runtimeConfig?.locationApiBaseUrl ?? '/api-location',
  prenotazioniApiBaseUrl: runtimeConfig?.prenotazioniApiBaseUrl ?? '/api-prenotazioni',
  floorPlanEditorUrl: runtimeConfig?.floorPlanEditorUrl ?? '/editor',
};
