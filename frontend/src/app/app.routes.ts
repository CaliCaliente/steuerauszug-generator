import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./generator/generator.component').then(m => m.GeneratorComponent)
  }
];
