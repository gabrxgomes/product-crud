import { Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ProductService } from '../../../core/services/product.service';

@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule],
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.css',
})
export class ProductForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly productId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: ['', [Validators.maxLength(1000)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    quantity: [0, [Validators.required, Validators.min(0)]],
  });

  get isEditMode(): boolean {
    return this.productId() !== null;
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      return;
    }
    const id = Number(idParam);
    this.productId.set(id);
    this.productService.findById(id).subscribe({
      next: (product) =>
        this.form.patchValue({
          name: product.name,
          description: product.description ?? '',
          price: product.price,
          quantity: product.quantity,
        }),
      error: () => this.errorMessage.set('Could not load the product to edit.'),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);
    const request = this.form.getRawValue();

    const result$ = this.isEditMode
      ? this.productService.update(this.productId()!, request)
      : this.productService.create(request);

    result$.subscribe({
      next: () => this.router.navigate(['/products']),
      error: () => {
        this.errorMessage.set('Could not save the product. Check the field values.');
        this.saving.set(false);
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/products']);
  }
}
