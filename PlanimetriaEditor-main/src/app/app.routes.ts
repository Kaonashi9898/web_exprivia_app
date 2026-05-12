import { Routes } from '@angular/router';
import { FloorPlanEditorComponent } from './floor-plan-editor/floor-plan-editor';

export const routes: Routes = [
    {
        path: '',
        component: FloorPlanEditorComponent
    },
    {
        path: 'editor',
        redirectTo: '',
        pathMatch: 'full'
    },
    {
        path: '**',
        redirectTo: ''
    }
];
