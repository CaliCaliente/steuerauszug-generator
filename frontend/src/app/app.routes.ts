import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./generator/generator.component').then(m => m.GeneratorComponent)
  },
  {
    path: 'validate',
    loadComponent: () =>
      import('./validator/validator.component').then(m => m.ValidatorComponent)
  }
];
