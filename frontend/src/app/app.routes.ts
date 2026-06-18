import { Routes } from '@angular/router';

import { ProductList } from './features/products/product-list/product-list.component';
import { ProductForm } from './features/products/product-form/product-form.component';

export const routes: Routes = [
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  { path: 'products', component: ProductList },
  { path: 'products/new', component: ProductForm },
  { path: 'products/:id/edit', component: ProductForm },
];
