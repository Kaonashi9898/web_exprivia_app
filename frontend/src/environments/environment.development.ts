import { RuntimeConfig } from './runtime-config';

const runtimeConfig = (globalThis as { __EXPRIVIA_CONFIG__?: Partial<RuntimeConfig> }).__EXPRIVIA_CONFIG__;

export const environment: RuntimeConfig = {
  utentiApiBaseUrl: runtimeConfig?.utentiApiBaseUrl ?? 'http://localhost:8081',
  locationApiBaseUrl: runtimeConfig?.locationApiBaseUrl ?? 'http://localhost:8082',
  prenotazioniApiBaseUrl: runtimeConfig?.prenotazioniApiBaseUrl ?? 'http://localhost:8083',
  floorPlanEditorUrl: runtimeConfig?.floorPlanEditorUrl ?? 'http://localhost:4202/editor',
};
