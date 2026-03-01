import { Component, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SteuerauszugService, ValidationResponse } from '../services/steuerausweis.service';

@Component({
  selector: 'app-validator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './validator.component.html',
  styleUrl: './validator.component.scss'
})
export class ValidatorComponent {
  protected selectedFile = signal<File | null>(null);
  protected isDragging = signal(false);
  protected loading = signal(false);
  protected result = signal<ValidationResponse | null>(null);
  protected error = signal<string | null>(null);

  constructor(private service: SteuerauszugService) {}

  get canValidate(): boolean {
    return !!this.selectedFile() && !this.loading();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile.set(input.files[0]);
      this.result.set(null);
      this.error.set(null);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(true);
  }

  onDragLeave(): void {
    this.isDragging.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging.set(false);
    if (event.dataTransfer?.files.length) {
      this.selectedFile.set(event.dataTransfer.files[0]);
      this.result.set(null);
      this.error.set(null);
    }
  }

  validate(): void {
    if (!this.canValidate) return;
    this.loading.set(true);
    this.result.set(null);
    this.error.set(null);

    this.service.validate(this.selectedFile()!).subscribe({
      next: (response) => {
        this.result.set(response);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message || 'Fehler bei der Validierung.');
        this.loading.set(false);
      }
    });
  }
}
