import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ValidatorComponent } from './validator.component';
import { SteuerauszugService } from '../services/steuerausweis.service';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';

describe('ValidatorComponent', () => {
  let component: ValidatorComponent;
  let fixture: ComponentFixture<ValidatorComponent>;
  let serviceSpy: jasmine.SpyObj<SteuerauszugService>;

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj('SteuerauszugService', ['validate']);

    await TestBed.configureTestingModule({
      imports: [ValidatorComponent],
      providers: [
        provideHttpClient(),
        { provide: SteuerauszugService, useValue: serviceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ValidatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not allow validation when no file is selected', () => {
    expect(component.canValidate).toBeFalse();
  });

  it('should allow validation when file is selected', () => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    component['selectedFile'].set(file);

    expect(component.canValidate).toBeTrue();
  });

  it('should set file on file selection', () => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    const event = { target: input } as unknown as Event;

    component.onFileSelected(event);

    expect(component['selectedFile']()).toBe(file);
  });

  it('should show loading state during validation', fakeAsync(() => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    component['selectedFile'].set(file);
    serviceSpy.validate.and.returnValue(of({ valid: true }).pipe());

    component.validate();

    expect(component['loading']()).toBeTrue();
    tick();
    fixture.detectChanges();
    expect(component['loading']()).toBeFalse();
  }));

  it('should display valid result when validation succeeds', fakeAsync(() => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    component['selectedFile'].set(file);
    serviceSpy.validate.and.returnValue(of({ valid: true }));

    component.validate();
    tick();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.result-box.valid')).toBeTruthy();
    expect(compiled.querySelector('.result-box.valid')!.textContent).toContain('Dokument ist gültig');
  }));

  it('should display invalid result with errors when validation fails', fakeAsync(() => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    component['selectedFile'].set(file);
    const errors = ['ERROR: missing required attribute'];
    serviceSpy.validate.and.returnValue(of({ valid: false, errors }));

    component.validate();
    tick();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.result-box.invalid')).toBeTruthy();
    expect(compiled.querySelector('.result-box.invalid')!.textContent).toContain('Dokument ist ungültig');
    expect(compiled.querySelector('.error-list')!.textContent).toContain('ERROR: missing required attribute');
  }));

  it('should display error message when service call fails', fakeAsync(() => {
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    component['selectedFile'].set(file);
    serviceSpy.validate.and.returnValue(throwError(() => new Error('Network error')));

    component.validate();
    tick();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error-box')).toBeTruthy();
  }));

  it('should handle drag over event', () => {
    const event = new DragEvent('dragover');
    spyOn(event, 'preventDefault');

    component.onDragOver(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(component['isDragging']()).toBeTrue();
  });

  it('should handle drag leave event', () => {
    component['isDragging'].set(true);

    component.onDragLeave();

    expect(component['isDragging']()).toBeFalse();
  });

  it('should reset result and error when new file is selected', () => {
    component['result'].set({ valid: true });
    component['error'].set('previous error');

    const file = new File(['content'], 'new.pdf', { type: 'application/pdf' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    const event = { target: input } as unknown as Event;

    component.onFileSelected(event);

    expect(component['result']()).toBeNull();
    expect(component['error']()).toBeNull();
  });
});
