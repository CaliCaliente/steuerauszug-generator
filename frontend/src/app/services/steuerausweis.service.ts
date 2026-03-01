import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GenerationRequest {
  taxYear: number;
  canton: string;
  clearingNumber: string;
  institutionName: string;
  institutionAddress: string;
  customerNumber: string;
  customerName: string;
  customerAddress: string;
}

@Injectable({ providedIn: 'root' })
export class SteuerauszugService {
  constructor(private http: HttpClient) {}

  generate(file: File, request: GenerationRequest): Observable<Blob> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append(
      'request',
      new Blob([JSON.stringify(request)], { type: 'application/json' })
    );
    return this.http.post('/api/steuerausweis/generate', formData, {
      responseType: 'blob'
    });
  }
}
