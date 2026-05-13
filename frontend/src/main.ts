import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig)
  .then(() => registerServiceWorker())
  .catch((err) => console.error(err));

function registerServiceWorker(): Promise<ServiceWorkerRegistration | void> {
  if (!('serviceWorker' in navigator)) {
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    const register = () => {
      navigator.serviceWorker.register('/service-worker.js')
        .then(resolve)
        .catch((error) => {
          console.warn('Service worker registration failed.', error);
          resolve();
        });
    };

    if (document.readyState === 'complete') {
      register();
      return;
    }

    globalThis.addEventListener('load', register, { once: true });
  });
}
