import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { SteuerauszugService, GenerationRequest } from '../services/steuerausweis.service';

@Component({
  selector: 'app-generator',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './generator.component.html',
  styleUrl: './generator.component.scss'
})
export class GeneratorComponent {
  selectedFile: File | null = null;
  isDragging = false;
  isLoading = false;
  error: string | null = null;

  readonly cantons = [
    'ZH', 'BE', 'LU', 'UR', 'SZ', 'OW', 'NW', 'GL', 'ZG', 'FR',
    'SO', 'BS', 'BL', 'SH', 'AR', 'AI', 'SG', 'GR', 'AG', 'TG',
    'TI', 'VD', 'VS', 'NE', 'GE', 'JU'
  ];

  form: FormGroup;

  constructor(private fb: FormBuilder, private service: SteuerauszugService) {
    this.form = this.fb.group({
      taxYear: [new Date().getFullYear() - 1, [Validators.required, Validators.min(2000), Validators.max(2100)]],
      canton: ['ZH', Validators.required],
      clearingNumber: ['', Validators.required],
      institutionName: ['', Validators.required],
      institutionAddress: ['', Validators.required],
      customerNumber: ['', Validators.required],
      customerName: ['', Validators.required],
      customerAddress: ['', Validators.required]
    });
  }

  get canGenerate(): boolean {
    return !!this.selectedFile && this.form.valid && !this.isLoading;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile = input.files[0];
      this.error = null;
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(): void {
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    if (event.dataTransfer?.files.length) {
      this.selectedFile = event.dataTransfer.files[0];
      this.error = null;
    }
  }

  generate(): void {
    if (!this.canGenerate) return;
    this.isLoading = true;
    this.error = null;

    const v = this.form.value;
    const request: GenerationRequest = {
      taxYear: v.taxYear,
      canton: v.canton,
      clearingNumber: v.clearingNumber,
      institutionName: v.institutionName,
      institutionAddress: v.institutionAddress,
      customerNumber: v.customerNumber,
      customerName: v.customerName,
      customerAddress: v.customerAddress
    };

    this.service.generate(this.selectedFile!, request).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `steuerausweis-${request.taxYear}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.isLoading = false;
      },
      error: (err) => {
        this.error = err.message || 'Fehler bei der Generierung. Bitte prüfen Sie die Datei und versuchen Sie es erneut.';
        this.isLoading = false;
      }
    });
  }
}
