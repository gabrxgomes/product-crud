import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProductList } from './product-list.component';
import { ProductService } from '../../../core/services/product.service';
import { Page, Product } from '../../../core/models/product';

function buildProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    name: 'Mouse',
    description: 'Wireless',
    price: 50,
    quantity: 3,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ProductList', () => {
  let component: ProductList;
  let fixture: ComponentFixture<ProductList>;
  let productService: { findAll: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    productService = { findAll: vi.fn(), delete: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [provideRouter([]), { provide: ProductService, useValue: productService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductList);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    productService.findAll.mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } as Page<Product>));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('loads products on init and exposes them via the signal', () => {
    const product = buildProduct();
    productService.findAll.mockReturnValue(
      of({ content: [product], totalElements: 1, totalPages: 1, number: 0, size: 20 } as Page<Product>),
    );

    fixture.detectChanges();

    expect(component.products()).toEqual([product]);
    expect(component.loading()).toBe(false);
  });

  it('sets an error message when loading fails', () => {
    productService.findAll.mockReturnValue(throwError(() => new Error('network error')));

    fixture.detectChanges();

    expect(component.errorMessage()).toContain('Could not load products');
  });

  it('removes the product from the list after a confirmed delete', () => {
    const product = buildProduct();
    productService.findAll.mockReturnValue(
      of({ content: [product], totalElements: 1, totalPages: 1, number: 0, size: 20 } as Page<Product>),
    );
    productService.delete.mockReturnValue(of(undefined));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    fixture.detectChanges();
    component.deleteProduct(product);

    expect(productService.delete).toHaveBeenCalledWith(product.id);
    expect(component.products()).toEqual([]);
  });

  it('does not call delete when the confirmation is dismissed', () => {
    const product = buildProduct();
    productService.findAll.mockReturnValue(
      of({ content: [product], totalElements: 1, totalPages: 1, number: 0, size: 20 } as Page<Product>),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    fixture.detectChanges();
    component.deleteProduct(product);

    expect(productService.delete).not.toHaveBeenCalled();
  });
});
