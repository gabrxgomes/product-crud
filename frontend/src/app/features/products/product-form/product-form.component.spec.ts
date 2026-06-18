import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProductForm } from './product-form.component';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product';

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

describe('ProductForm', () => {
  let component: ProductForm;
  let fixture: ComponentFixture<ProductForm>;
  let productService: {
    findById: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  function setup(routeId: string | null) {
    productService = { findById: vi.fn(), create: vi.fn(), update: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: productService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(routeId ? { id: routeId } : {}) } },
        },
      ],
    });

    fixture = TestBed.createComponent(ProductForm);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  describe('create mode', () => {
    beforeEach(() => setup(null));

    it('should create with an empty form', () => {
      fixture.detectChanges();
      expect(component.isEditMode).toBe(false);
      expect(component.form.value).toEqual({ name: '', description: '', price: 0, quantity: 0 });
    });

    it('does not submit when the form is invalid', () => {
      fixture.detectChanges();
      component.submit();

      expect(productService.create).not.toHaveBeenCalled();
      expect(component.form.controls.name.touched).toBe(true);
    });

    it('calls create and navigates back to the list on success', () => {
      fixture.detectChanges();
      const created = buildProduct();
      productService.create.mockReturnValue(of(created));

      component.form.setValue({ name: 'Mouse', description: 'Wireless', price: 50, quantity: 3 });
      component.submit();

      expect(productService.create).toHaveBeenCalledWith({
        name: 'Mouse',
        description: 'Wireless',
        price: 50,
        quantity: 3,
      });
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });

    it('surfaces an error message when create fails', () => {
      fixture.detectChanges();
      productService.create.mockReturnValue(throwError(() => new Error('boom')));

      component.form.setValue({ name: 'Mouse', description: '', price: 50, quantity: 3 });
      component.submit();

      expect(component.errorMessage()).toContain('Could not save');
    });
  });

  describe('edit mode', () => {
    beforeEach(() => setup('1'));

    it('loads the existing product and patches the form', () => {
      const existing = buildProduct();
      productService.findById.mockReturnValue(of(existing));

      fixture.detectChanges();

      expect(component.isEditMode).toBe(true);
      expect(productService.findById).toHaveBeenCalledWith(1);
      expect(component.form.value).toEqual({
        name: 'Mouse',
        description: 'Wireless',
        price: 50,
        quantity: 3,
      });
    });

    it('calls update with the product id on submit', () => {
      productService.findById.mockReturnValue(of(buildProduct()));
      fixture.detectChanges();
      productService.update.mockReturnValue(of(buildProduct({ name: 'Mouse Pro' })));

      component.form.patchValue({ name: 'Mouse Pro' });
      component.submit();

      expect(productService.update).toHaveBeenCalledWith(1, {
        name: 'Mouse Pro',
        description: 'Wireless',
        price: 50,
        quantity: 3,
      });
      expect(router.navigate).toHaveBeenCalledWith(['/products']);
    });
  });
});
