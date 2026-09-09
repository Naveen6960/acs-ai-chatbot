import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MsalService } from '@azure/msal-angular';

// -------- Types mirroring your backend --------
export interface QuestionRequest {
  question: string;
  includeSummary?: boolean;
  maxDocuments?: number;
  employeeId?: string;
}

export interface DocumentReference {
  fileName: string;
  pageNumber: number;
  relevanceScore: number;
  contentSnippet: string;
  docPath: string;
}

export interface AskStructuredResponse {
  answer: string;
  bullets: string[];
  citations: { fileName: string; pageNumber: number; docPath: string }[];
  references: DocumentReference[];
  summary: string | null;
  totalDocumentsSearched: number;
}

export interface DocsList {
  count: number;
  documents: { fileName: string; docPath: string }[];
}

const API_BASE = 'http://localhost:8081';
//const API_BASE = 'http://10.239.8.101:8080';
//const API_BASE = 'https://backend-java-api-app.orangemushroom-13f71d93.eastus2.azurecontainerapps.io';

@Injectable({ providedIn: 'root' })
export class PdfqaService {
  constructor(private http: HttpClient, private authService: MsalService) {}

  askStructured(req: QuestionRequest): Observable<AskStructuredResponse> {
    const accounts = this.authService.instance.getAllAccounts();
    if (accounts.length > 0) {
      this.authService.instance.setActiveAccount(accounts[0]);
    }
    const account = this.authService.instance.getActiveAccount();
    req.employeeId = account?.username || 'unknown';

    return this.http.post<AskStructuredResponse>(
      `${API_BASE}/api/pdfqa/ask-structured`,
      req
    );
  }

  listDocs(): Observable<DocsList> {
    return this.http.get<DocsList>(`${API_BASE}/api/pdfqa/docs`);
  }
}